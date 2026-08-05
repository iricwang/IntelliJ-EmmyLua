/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.ue

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.ide.BuiltInServerManager
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * UE 蓝图注解管理器（编排核心）。
 *
 * 职责：接收引擎（UnLangService）经 [LuaUEBlueprintHttpHandler] 推送的界面蓝图注解文本 →
 * 按 WBP 单文件粒度写入 IDE 私有缓存目录（不落 Lua 工程、不进版本库）→ 防抖 VFS 刷新。
 * 缓存目录由 [LuaUEBlueprintLibraryProvider] 以 SyntheticLibrary 挂载，平台按单文件粒度
 * 自动重建索引——无全量重建、无 stub forceRebuild。
 *
 * 多开支持：引擎广播时各 IDE 用 [matches] 按 UE 工程目录自匹配；本类同时维护
 * [getRegistryDir] 下的 IDE 注册表文件（[registerIde]/dispose 删除），引擎枚举该目录
 * 发现所有在线 IDE。
 */
@Service(Service.Level.PROJECT)
class LuaUEBlueprintManager(private val project: Project) : Disposable {

    companion object {
        private const val CACHE_ROOT_NAME = "lua-ue-defs"
        private const val REFRESH_DEBOUNCE_MS = 500L

        private val LOG = Logger.getInstance(LuaUEBlueprintManager::class.java)

        fun getInstance(project: Project): LuaUEBlueprintManager =
            project.getService(LuaUEBlueprintManager::class.java)

        /**
         * IDE 注册表目录（跨 IDE 实例共享的约定位置）。引擎侧枚举此目录下的 *.json
         发现所有在线 IDE 及其端口/工程路径。
         */
        fun getRegistryDir(): File {
            val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotEmpty() }
                ?: "${System.getProperty("user.home")}/.cache"
            return File(base, "EmmyLuaDefs/ide-registry")
        }
    }

    /** 防抖序号：每次调度自增，只有最新一次延迟任务真正执行。 */
    private val refreshSeq = AtomicInteger(0)

    /** 批量推送期间（begin-batch/end-batch）抑制 VFS 刷新。 */
    @Volatile
    private var batching = false

    /** 缓存目录当前是否处于挂载状态（有内容），用于只在挂载状态跃迁时触发 roots change。 */
    @Volatile
    private var mounted = false

    /** 上一次已知的缓存目录文件名集合，用于判断是否需要触发 roots change。 */
    @Volatile
    private var knownFileNames: Set<String> = emptySet()

    private var registryFile: File? = null

    /** 本项目专属的缓存目录（磁盘路径），按 project.locationHash 隔离。 */
    fun getCacheDir(): File {
        val base = File(PathManager.getSystemPath(), CACHE_ROOT_NAME)
        return File(base, project.locationHash)
    }

    /** 缓存目录的 VirtualFile；不存在时返回 null（供 LibraryProvider 判断是否挂载）。 */
    fun getCacheRoot(): VirtualFile? {
        val dir = getCacheDir()
        if (!dir.isDirectory) return null
        return VfsUtil.findFileByIoFile(dir, true)
    }

    /** 该文件是否为本工程的蓝图注解缓存文件（引擎推送的 WBP 注解，hover 追加「打开蓝图」链接用）。 */
    fun isBlueprintDefFile(file: VirtualFile): Boolean =
        file.path.startsWith(getCacheDir().invariantSeparatorsPath + "/")

    // ---- UE 工程匹配 ----

    /**
     * 本 IDEA 工程关联的 UE 工程目录：手动覆盖优先；否则从工程根向上探测
     * 含 *.uproject 的目录；探测失败返回空串。
     */
    fun resolveUeProjectDir(): String {
        val overrideDir = LuaUEBlueprintSettings.getInstance(project).ueProjectDir.trim()
        if (overrideDir.isNotEmpty()) return File(overrideDir).invariantSeparatorsPath.trimEnd('/')
        var dir = project.basePath?.let { File(it) }
        while (dir != null) {
            if (dir.listFiles { f -> f.extension.equals("uproject", ignoreCase = true) }?.isNotEmpty() == true) {
                return dir.invariantSeparatorsPath
            }
            dir = dir.parentFile
        }
        return ""
    }

    /** 判断引擎推送的 X-UE-Project 是否属于本工程（广播自过滤）。 */
    fun matches(ueProject: String): Boolean {
        val ue = File(ueProject).invariantSeparatorsPath.trimEnd('/')
        if (ue.isEmpty()) return false
        val local = resolveUeProjectDir()
        if (local.isNotEmpty()) return local.equals(ue, ignoreCase = true)
        // 未配置且探测失败时兜底：路径互为前缀即视为相关（Lua 工程在 UE 工程内或反之）。
        val base = project.basePath?.let { File(it).invariantSeparatorsPath.trimEnd('/') } ?: return false
        return ue.startsWith("$base/", ignoreCase = true) || base.startsWith("$ue/", ignoreCase = true)
    }

    // ---- 注解写入（由 HttpHandler 调用，运行在内建服务器 IO 线程） ----

    /** upsert：把 relPath（如 "GamePlay/WBP_BagItem.lua"）的注解文本写入缓存目录。 */
    fun upsertDef(relPath: String, content: String): Boolean {
        val file = resolveDefFile(relPath) ?: return false
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        scheduleRefresh()
        return true
    }

    fun deleteDef(relPath: String): Boolean {
        val file = resolveDefFile(relPath) ?: return false
        val existed = file.delete()
        if (existed) scheduleRefresh()
        return existed
    }

    /** clear：清空本工程全部 UE 注解（引擎全量重同步前先 clear 再批量 upsert）。 */
    fun clearDefs() {
        getCacheDir().listFiles()?.forEach { it.deleteRecursively() }
        scheduleRefresh()
    }

    fun beginBatch() {
        batching = true
    }

    fun endBatch() {
        batching = false
        scheduleRefresh()
    }

    /** 路径消毒：拒绝绝对路径与 .. 逃逸，统一分隔符。 */
    private fun resolveDefFile(relPath: String): File? {
        val rel = relPath.replace('\\', '/').trimStart('/')
        if (rel.isEmpty() || rel.split('/').any { it == ".." }) return null
        return File(getCacheDir(), rel)
    }

    // ---- 刷新 ----

    private fun scheduleRefresh() {
        if (batching) return
        val seq = refreshSeq.incrementAndGet()
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            {
                if (seq == refreshSeq.get() && !batching) refreshNow()
            },
            REFRESH_DEBOUNCE_MS, TimeUnit.MILLISECONDS
        )
    }

    /** 立即刷新：VFS 异步刷新缓存目录；仅在文件集合发生增删时触发 roots change。
     *  roots change 会触发全项目依赖 rescan，代价高，不能每次内容变更都做。
     *  纯内容变更：文件已被上一轮 LibraryProvider 标记 PREDEFINED_KEY，VFS 刷新后平台自动重建其索引。
     *  增删文件：必须重新调用 getAdditionalProjectLibraries 才能给新文件打上 PREDEFINED_KEY。 */
    fun refreshNow() {
        val dir = getCacheDir()
        val names = if (dir.isDirectory) (dir.list()?.toSet() ?: emptySet()) else emptySet()
        val hasContent = names.isNotEmpty()
        VfsUtil.markDirtyAndRefresh(true, true, true, dir)
        mounted = hasContent

        val fileSetChanged = names != knownFileNames
        knownFileNames = names
        if (hasContent && fileSetChanged) {
            ApplicationManager.getApplication().invokeLater {
                WriteAction.run<RuntimeException> {
                    ProjectRootManagerEx.getInstanceEx(project)
                        .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)
                }
            }
        }
    }

    /** 启动时初始化挂载状态（避免首次推送时误判跃迁）。 */
    fun initMounted() {
        val dir = getCacheDir()
        knownFileNames = if (dir.isDirectory) (dir.list()?.toSet() ?: emptySet()) else emptySet()
        mounted = knownFileNames.isNotEmpty()
    }

    // ---- IDE 注册表 ----

    /**
     * 写入本工程的 IDE 注册表文件（引擎侧枚举 [getRegistryDir] 发现 IDE）。
     * 内容：内建服务器端口、IDEA 工程根、关联 UE 工程目录、PID、启动时间。
     */
    fun registerIde() {
        try {
            val port = BuiltInServerManager.getInstance().port
            if (port <= 0) {
                LOG.warn("UE 蓝图注解：内建 Web Server 不可用（端口 $port），引擎将无法推送。" +
                        "请检查 Settings | Build, Execution, Deployment | Debugger | Built-in server 设置。")
                return
            }
            val dir = getRegistryDir()
            dir.mkdirs()
            // 先删同 locationHash 的陈旧文件（不同 pid 的，IDE 多次启动后堆积端口过时的文件，
            // 引擎侧 pid 校验只看内容 pid 存活，同 pid 多文件不同端口会导致连死端口）。
            val currentPid = ProcessHandle.current().pid()
            dir.listFiles { f ->
                f.name.endsWith("-${project.locationHash}.json") &&
                    !f.name.startsWith("$currentPid-")
            }?.forEach { it.delete() }
            val file = File(dir, "$currentPid-${project.locationHash}.json")
            val json = buildString {
                append("{\n")
                append("  \"pid\": ").append(ProcessHandle.current().pid()).append(",\n")
                append("  \"port\": ").append(port).append(",\n")
                append("  \"ide\": \"idea\",\n")
                append("  \"projectBasePath\": \"").append(escapeJson(project.basePath ?: "")).append("\",\n")
                append("  \"ueProjectDir\": \"").append(escapeJson(resolveUeProjectDir())).append("\",\n")
                append("  \"startedAt\": ").append(System.currentTimeMillis()).append("\n")
                append("}\n")
            }
            file.writeText(json, Charsets.UTF_8)
            registryFile = file
        } catch (e: Exception) {
            LOG.warn("UE 蓝图注解：写入 IDE 注册表失败，引擎将无法发现本 IDE: ${e.message}", e)
        }
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    override fun dispose() {
        registryFile?.delete()
        registryFile = null
    }
}
