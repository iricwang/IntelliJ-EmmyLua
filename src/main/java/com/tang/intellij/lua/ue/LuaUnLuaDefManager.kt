/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the License);
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
 * UnLua IntelliSense 注解接收端（与 [LuaUEBlueprintManager] 同构，独立缓存目录）。
 *
 * 接收 UnLangServer 触发 UnLua Generate IntelliSense 后经 [LuaUnLuaDefHttpHandler]
 * 推送的注解文件，按单文件粒度写入 IDE 私有缓存目录（不落 Lua 工程、不进版本库），
 * 防抖 VFS 刷新。缓存目录由 [LuaUnLuaDefLibraryProvider] 以 SyntheticLibrary 挂载。
 *
 * 工程匹配复用 [LuaUEBlueprintManager.matches]（同一 UE 工程目录探测逻辑）。
 * IDE 注册表不重复维护——引擎发现 IDE 的机制走 [LuaUEBlueprintManager.registerIde]。
 */
@Service(Service.Level.PROJECT)
class LuaUnLuaDefManager(private val project: Project) : Disposable {

    companion object {
        private const val CACHE_ROOT_NAME = "lua-unla-defs"
        private const val REFRESH_DEBOUNCE_MS = 500L

        fun getInstance(project: Project): LuaUnLuaDefManager =
            project.getService(LuaUnLuaDefManager::class.java)
    }

    private val refreshSeq = AtomicInteger(0)

    @Volatile
    private var batching = false

    @Volatile
    private var mounted = false

    /** 上一次已知的缓存目录文件名集合，用于判断是否需要触发 roots change。 */
    @Volatile
    private var knownFileNames: Set<String> = emptySet()

    fun getCacheDir(): File {
        val base = File(PathManager.getSystemPath(), CACHE_ROOT_NAME)
        return File(base, project.locationHash)
    }

    fun getCacheRoot(): VirtualFile? {
        val dir = getCacheDir()
        if (!dir.isDirectory) return null
        return VfsUtil.findFileByIoFile(dir, true)
    }

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

    private fun resolveDefFile(relPath: String): File? {
        val rel = relPath.replace('\\', '/').trimStart('/')
        if (rel.isEmpty() || rel.split('/').any { it == ".." }) return null
        return File(getCacheDir(), rel)
    }

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

    /** 立即刷新：VFS 异步刷新缓存目录；仅在文件集合发生增删时触发 roots change（代价高，见蓝图 Manager 注释）。 */
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

    fun initMounted() {
        val dir = getCacheDir()
        knownFileNames = if (dir.isDirectory) (dir.list()?.toSet() ?: emptySet()) else emptySet()
        mounted = knownFileNames.isNotEmpty()
    }

    override fun dispose() {}
}
