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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import com.intellij.util.io.HttpRequests
import java.io.File
import java.net.HttpURLConnection

/**
 * MVVM 绑定字段注解管理器：请求引擎 `/api/delegate-exports`
 * （UnLangService EmmyLuaIdeServer 读工程内 Source/LuaScripts/DelegateExports.txt，
 * 每行 `UClass名,别名`，反射组件类 GetXxx/SetXxx 推导可绑定属性），
 * 拿回生成的 ViewModelFields + 各别名类的 EmmyLua 注解，
 * 写入 IDE 私有缓存目录，由 [LuaUEReflectLibraryProvider] 以 SyntheticLibrary 挂载。
 *
 * 类型清单配置在 Lua 工程里的 DelegateExports.txt（唯一事实源），IDE 侧无列表配置。
 *
 * 触发时机：工程打开（[LuaUEBlueprintStartupActivity]）、设置页 apply、
 * 手动刷新动作（[LuaUERefreshReflectAction]）。引擎离线时静默记日志（手动动作除外，会通知）。
 *
 * 协议：POST 127.0.0.1:{port}/api/delegate-exports
 *      → {"defs": {"ViewModelFields": "---@class ViewModelFields ..."}, "missing": [...]}
 */
@Service(Service.Level.PROJECT)
class LuaUEReflectManager(private val project: Project) : Disposable {

    companion object {
        private const val CACHE_ROOT_NAME = "lua-reflect-defs"
        private val LOG = Logger.getInstance(LuaUEReflectManager::class.java)

        fun getInstance(project: Project): LuaUEReflectManager =
            project.getService(LuaUEReflectManager::class.java)
    }

    /** 本项目专属的缓存目录（按 project.locationHash 隔离）。 */
    fun getCacheDir(): File =
        File(File(PathManager.getSystemPath(), CACHE_ROOT_NAME), project.locationHash)

    /** 缓存目录的 VirtualFile；不存在时返回 null（供 LibraryProvider 判断是否挂载）。 */
    fun getCacheRoot(): VirtualFile? {
        val dir = getCacheDir()
        if (!dir.isDirectory) return null
        return VfsUtil.findFileByIoFile(dir, true)
    }

    /**
     * 拉取刷新：读 DelegateExports.txt（路径见 [LuaUEBlueprintSettings.delegateExportsPath]）
     * 的内容发给引擎生成绑定字段注解，写缓存目录。
     * @param notifyOnFailure true（手动触发）时失败弹通知；false（自动触发）时静默记日志。
     */
    fun refresh(notifyOnFailure: Boolean = false) {
        val settings = LuaUEBlueprintSettings.getInstance(project)
        val configFile = settings.resolveDelegateExportsFile()
        val content = runCatching { configFile.readText(Charsets.UTF_8) }.getOrNull()
        if (content == null) {
            LOG.info("delegate-exports 配置不可读: ${configFile.path}")
            if (notifyOnFailure) {
                LuaUEWidgetOpener.notifyWarning(
                    project, "绑定字段刷新失败",
                    "DelegateExports 配置不可读：${configFile.path}（设置页可改路径）"
                )
            }
            return
        }
        val port = settings.ueBridgePort
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val (defs, missing) = fetchDefs(port, content)
                if (missing.isNotEmpty()) {
                    LOG.info("delegate-exports 未找到组件类: $missing")
                }
                applyDefs(defs)
                if (notifyOnFailure) {
                    when {
                        defs.isEmpty() -> LuaUEWidgetOpener.notifyWarning(
                            project, "绑定字段刷新", "引擎未返回任何定义（未找到: $missing）"
                        )
                        missing.isNotEmpty() -> LuaUEWidgetOpener.notifyWarning(
                            project, "绑定字段刷新", "部分组件类未找到: $missing"
                        )
                    }
                }
            } catch (e: Exception) {
                LOG.info("delegate-exports 拉取失败(127.0.0.1:$port)：${e.message}")
                if (notifyOnFailure) {
                    LuaUEWidgetOpener.notifyWarning(project, "绑定字段刷新失败", "连接引擎失败(127.0.0.1:$port)：${e.message}")
                }
            }
        }
    }

    /** 把拉取到的注解写盘并刷新 VFS；首次产出（挂载跃迁）时触发 roots change。 */
    internal fun applyDefs(defs: Map<String, String>) {
        val dir = getCacheDir()
        val wasMounted = dir.listFiles { f -> f.extension == "lua" }?.isNotEmpty() == true

        // 删除不在本次结果里的旧文件，写入/覆盖本次定义。
        dir.listFiles { f -> f.extension == "lua" }
            ?.filter { it.nameWithoutExtension !in defs.keys }
            ?.forEach { it.delete() }
        if (defs.isNotEmpty()) {
            dir.mkdirs()
            defs.forEach { (name, annotation) ->
                File(dir, "$name.lua").writeText(annotation, Charsets.UTF_8)
            }
        }
        val isMounted = defs.isNotEmpty()

        if (defs.isNotEmpty() || wasMounted) {
            VfsUtil.markDirtyAndRefresh(true, true, true, dir)
        }
        if (isMounted != wasMounted) {
            ApplicationManager.getApplication().invokeLater {
                WriteAction.run<RuntimeException> {
                    ProjectRootManagerEx.getInstanceEx(project)
                        .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)
                }
            }
        }
    }

    /** @return (类型名→注解文本, 未找到的组件类名) */
    private fun fetchDefs(port: Int, content: String): Pair<Map<String, String>, List<String>> {
        val req = JsonObject().apply { addProperty("content", content) }
        val body = HttpRequests.post("http://127.0.0.1:$port/api/delegate-exports", "application/json")
            .connectTimeout(3500)
            .readTimeout(60000) // 首次反射多个组件类较慢
            .connect { request ->
                request.write(req.toString())
                val conn = request.connection as HttpURLConnection
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                code to stream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            }
        val (code, text) = body
        val resp = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: throw RuntimeException("引擎响应无法解析(HTTP $code)：${text.take(200)}")
        resp.get("error")?.let { throw RuntimeException(it.asString) }

        val defs = mutableMapOf<String, String>()
        resp.getAsJsonObject("defs")?.entrySet()?.forEach { (k, v) -> defs[k] = v.asString }
        val missing = resp.getAsJsonArray("missing")?.map { it.asString }.orEmpty()
        return defs to missing
    }

    override fun dispose() {}
}
