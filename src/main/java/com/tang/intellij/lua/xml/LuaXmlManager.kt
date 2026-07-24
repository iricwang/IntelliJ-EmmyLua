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

package com.tang.intellij.lua.xml

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
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * XML 类型定义管理器（编排核心）。
 *
 * 职责：扫描 [LuaXmlSettings.xmlDefDir] 下的 XML → 转译为 emmylua 注释文本 → 按体积分包写入
 * IDE 私有缓存目录（对用户透明、不落项目、不进版本库）→ 内容有变化时触发合成库刷新。
 * 分包是因为超过 idea.max.intellisense.filesize 的文件不会建 stub 索引，
 * 单文件一旦超限（部分环境该值被调到 ~1MB）全局补全会整体失效。
 *
 * 缓存目录随后由 [LuaXmlLibraryProvider] 以 SyntheticLibrary 形式挂入项目，走完整 stub index，
 * 因此补全/跳转/查找引用/Goto 全部复用 EmmyLua 既有引擎。
 *
 * 线程模型：[rebuild]/[scheduleRebuild] 可在任意线程调用；文件 IO 在池线程串行执行，
 * roots change 切回 EDT。[scheduleRebuild] 带防抖，供 XML 目录变动/设置变更等高频触发源使用。
 */
@Service(Service.Level.PROJECT)
class LuaXmlManager(private val project: Project) : Disposable {

    companion object {
        private const val CACHE_ROOT_NAME = "lua-xml-defs"
        private const val DEFS_FILE_PREFIX = "defs_part"
        private const val DEFS_FILE_SUFFIX = ".lua"

        /** 每个 part 文件头部（沿用旧 Python 生成器的提示语）。 */
        private const val DEFS_HEADER = "---自动生成的协议辅助内容，请勿修改，有问题请找： wangzhilin01。\n\n"

        /**
         * 单个 part 文件的最大字节数。必须低于 idea.max.intellisense.filesize——超限文件
         * 不会建 stub 索引（编辑器和全局补全都会失效）。该属性 IDE 默认 2.5MB，但实际
         * 环境可能被调到 ~1MB，这里取 900KB 留足余量。
         */
        private const val MAX_PART_BYTES = 900 * 1024

        private const val REBUILD_DEBOUNCE_MS = 500

        private val LOG = Logger.getInstance(LuaXmlManager::class.java)

        fun getInstance(project: Project): LuaXmlManager =
            project.getService(LuaXmlManager::class.java)
    }

    /** 串行化重建过程（重建很快，直接互斥即可）。 */
    private val rebuildLock = Any()

    /** 防抖序号：每次 [scheduleRebuild] 自增，只有最新一次延迟任务真正执行。 */
    private val rebuildSeq = AtomicInteger(0)

    /** 缓存目录当前是否有内容（挂载状态），用于识别跃迁。 */
    @Volatile
    private var mounted = false

    /** 本项目专属的缓存目录（磁盘路径），按 project.locationHash 隔离，避免多项目冲突。 */
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

    /** 防抖调度重建（XML 目录变动、设置变更等高频触发源使用）。 */
    fun scheduleRebuild() {
        val seq = rebuildSeq.incrementAndGet()
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            { if (seq == rebuildSeq.get()) rebuild() },
            REBUILD_DEBOUNCE_MS.toLong(), TimeUnit.MILLISECONDS
        )
    }

    /**
     * 重建结果。
     * - [UNCHANGED]：内容无变化，零开销跳过。
     * - [CONTENT_CHANGED]：part 文件内容变化——VFS 刷新即可，平台按文件粒度自动失效索引。
     * - [MOUNT_CHANGED]：缓存目录从无到有/从有到无（挂载跃迁）——额外需要 roots change。
     */
    private enum class RebuildOutcome { UNCHANGED, CONTENT_CHANGED, MOUNT_CHANGED }

    /**
     * 重新解析：扫描 [LuaXmlSettings.xmlDefDir] 下的 *.xml → 转译按体积分包写入缓存目录 →
     * 仅当内容有变化时 VFS 刷新；挂载跃迁时额外 roots change。
     * 目录未配置 / 不存在时清除缓存（相当于卸载 XML 定义）。
     *
     * 注意：缓存是真实本地文件，VFS 内容事件会让平台自动按文件粒度重建索引，
     * 无需 StubIndex.forceRebuild 全量重建（区别于 std 库 jar 内文件的 StdLibraryProvider.reload）。
     */
    fun rebuild() {
        val app = ApplicationManager.getApplication()
        app.executeOnPooledThread {
            when (synchronized(rebuildLock) { rebuildLocked() }) {
                RebuildOutcome.UNCHANGED -> {}
                RebuildOutcome.CONTENT_CHANGED ->
                    VfsUtil.markDirtyAndRefresh(true, true, true, getCacheDir())
                RebuildOutcome.MOUNT_CHANGED -> {
                    VfsUtil.markDirtyAndRefresh(true, true, true, getCacheDir())
                    app.invokeLater {
                        WriteAction.run<RuntimeException> {
                            ProjectRootManagerEx.getInstanceEx(project)
                                .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)
                        }
                    }
                }
            }
        }
    }

    /** @return 见 [RebuildOutcome]。 */
    private fun rebuildLocked(): RebuildOutcome {
        val settings = LuaXmlSettings.getInstance(project)
        val cacheDir = getCacheDir()

        val srcDir = settings.xmlDefDir.trim().takeIf { it.isNotEmpty() }?.let { File(it) }
        val parts = if (srcDir != null && srcDir.isDirectory) {
            splitParts(collectSnippets(srcDir))
        } else emptyList()

        // 与现有缓存逐包比对，全部一致则零开销跳过。
        val existing = cacheDir.listFiles { f -> f.name.startsWith(DEFS_FILE_PREFIX) && f.name.endsWith(DEFS_FILE_SUFFIX) }
            ?.sortedBy { it.name }.orEmpty()
        if (parts.isNotEmpty() && existing.size == parts.size) {
            val same = existing.zip(parts).all { (file, content) ->
                runCatching { file.readText(Charsets.UTF_8) }.getOrNull() == content
            }
            if (same) {
                mounted = true
                return RebuildOutcome.UNCHANGED
            }
        }

        // 有变化：先清掉旧包（含历史单文件 defs.lua），再写入新包。
        existing.forEach { it.delete() }
        File(cacheDir, "defs.lua").delete()
        if (parts.isEmpty()) {
            val wasMounted = mounted
            mounted = false
            return if (wasMounted || existing.isNotEmpty()) RebuildOutcome.MOUNT_CHANGED else RebuildOutcome.UNCHANGED
        }

        cacheDir.mkdirs()
        parts.forEachIndexed { index, content ->
            File(cacheDir, "$DEFS_FILE_PREFIX${index + 1}$DEFS_FILE_SUFFIX").writeText(content, Charsets.UTF_8)
        }
        val wasMounted = mounted
        mounted = true
        return if (wasMounted) RebuildOutcome.CONTENT_CHANGED else RebuildOutcome.MOUNT_CHANGED
    }

    /**
     * 把片段按体积分包：单片段不跨包切分，累计超过 [MAX_PART_BYTES] 开新包；
     * 单个片段自身超阈值时独占一个包（并由使用者决定是否需要提高 IDE 限制）。
     */
    private fun splitParts(snippets: List<String>): List<String> {
        if (snippets.isEmpty()) return emptyList()
        val headerBytes = DEFS_HEADER.toByteArray(Charsets.UTF_8).size
        val parts = mutableListOf<String>()
        val current = StringBuilder(DEFS_HEADER)
        var currentBytes = headerBytes

        fun flush() {
            parts += current.toString()
            current.setLength(0)
            current.append(DEFS_HEADER)
            currentBytes = headerBytes
        }

        for (snippet in snippets) {
            val text = if (snippet.endsWith("\n")) snippet else snippet + "\n"
            val bytes = text.toByteArray(Charsets.UTF_8).size
            if (currentBytes > headerBytes && currentBytes + bytes > MAX_PART_BYTES) flush()
            current.append(text)
            currentBytes += bytes
        }
        flush()
        return parts
    }

    /**
     * 递归收集目录下所有 *.xml，逐个转译为非空片段（保持文件遍历顺序稳定，产物可复现）。
     *
     * 全部文件共享一个定义去重集（先见先得）：路径排序保证 alias_parts 先于 game_play/lobby
     * 处理，RPC 模块 Aliases 段里与 alias_parts 重名的定义会被跳过，与旧生成器一致。
     * 开头注入旧生成器硬编码的 4 个基础别名（UINT/INT/SIMPLE_TABLE/simplelua）。
     *
     * 每个片段用 `--region <相对路径>` 包裹：单文件产物里可直接按 XML 路径搜索验证覆盖情况，
     * 并在编辑器里按模块折叠。
     */
    private fun collectSnippets(srcDir: File): List<String> {
        val emittedDefs = mutableSetOf<String>()
        val snippets = mutableListOf<String>()
        snippets += "--region <base aliases>\n" + XmlToLuaTranspiler.baseAliasesSnippet(emittedDefs) + "--endregion\n"
        srcDir.walkTopDown()
            .onEnter { it.name != ".svn" }
            .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
            .sortedBy { it.invariantSeparatorsPath }
            .forEach { file ->
                val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
                if (text == null) {
                    LOG.warn("Lua XML defs: 读取失败，已跳过: ${file.path}")
                    return@forEach
                }
                val module = file.nameWithoutExtension
                val gamePlay = "/game_play/" in file.invariantSeparatorsPath
                val out = XmlToLuaTranspiler.transpile(text, module, gamePlay, emittedDefs)
                if (out.isBlank()) {
                    // alias.xml/entities.xml 等索引文件无产出属正常；其余情况多为解析失败，留痕便于排查。
                    LOG.info("Lua XML defs: 转译无产出: ${file.path}")
                    return@forEach
                }
                val rel = file.relativeTo(srcDir).invariantSeparatorsPath
                snippets += "--region $rel\n$out--endregion\n"
            }
        return snippets
    }

    override fun dispose() {}
}
