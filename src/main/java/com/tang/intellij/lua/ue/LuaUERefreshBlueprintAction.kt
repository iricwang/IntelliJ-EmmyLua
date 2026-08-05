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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.net.HttpURLConnection

/**
 * 手动触发引擎全量重建 UE 界面蓝图注解（Tools 菜单）。
 *
 * 请求 UnLangService `POST 127.0.0.1:{ueBridgePort}/api/refresh-ue-defs`，body 携带
 * 本工程的注解缓存目录（{"cache_dir": ".../lua-ue-defs/<hash>"}）。引擎立即响应
 * （accepted 语义），随后在游戏线程分帧导出 WBP 注解，**直接写入该缓存目录**
 * （清旧写新），完成后只经 [/api/ue-defs][LuaUEBlueprintHttpHandler] 发一对
 * begin-batch/end-batch 轻量信号，IDE 收到 end-batch 后 VFS 刷新 + 按文件粒度重建索引。
 * 旧版引擎（不识别 cache_dir）仍走逐文件 HTTP 回推，两侧协议向后兼容。
 *
 * 单个 WBP 编译回调不回写缓存目录，仍由引擎异步 upsert 推送到 IDE 落盘。
 */
class LuaUERefreshBlueprintAction : AnAction("刷新 UE 界面蓝图类型注解") {

    companion object {
        private val LOG = Logger.getInstance(LuaUERefreshBlueprintAction::class.java)
        // 引擎收到请求即响应（不等业务执行完），10s 足够覆盖极端繁忙场景；
        // 旧版引擎会把响应挂到游戏线程 ticker 上，长超时只会让失败提示迟到。
        private const val READ_TIMEOUT_MS = 10000
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val port = LuaUEBlueprintSettings.getInstance(project).ueBridgePort
        // 直写模式：把本工程注解缓存目录交给引擎（先确保目录存在，引擎才能直接写盘）。
        val cacheDir = LuaUEBlueprintManager.getInstance(project).getCacheDir()
        cacheDir.mkdirs()
        val cacheDirPath = cacheDir.invariantSeparatorsPath
        ApplicationManager.getApplication().executeOnPooledThread {
            val error = runCatching { requestRefresh(port, cacheDirPath) }
                .getOrElse {
                    if (it is java.net.SocketTimeoutException) {
                        "引擎响应超时(127.0.0.1:$port)：编辑器可能正忙（PIE/资产加载中），请稍后重试；" +
                            "若持续超时请确认引擎侧 UnLangService 已更新到最新版本"
                    } else {
                        "连接引擎失败(127.0.0.1:$port)：${it.message}"
                    }
                }
            if (error != null) {
                LOG.info("refresh-ue-defs 失败: $error")
                LuaUEWidgetOpener.notifyWarning(project, "刷新 UE 蓝图注解失败", error)
            } else {
                LuaUEWidgetOpener.notifyWarning(project, "刷新 UE 蓝图注解", "已请求引擎全量重建，导出完成后注解自动生效")
            }
        }
    }

    /** @return null 表示成功；否则为错误描述。 */
    private fun requestRefresh(port: Int, cacheDir: String): String? {
        val req = JsonObject().apply { addProperty("cache_dir", cacheDir) }
        val (code, body) = HttpRequests.post("http://127.0.0.1:$port/api/refresh-ue-defs", "application/json")
            .connectTimeout(3500)
            .readTimeout(READ_TIMEOUT_MS)
            .connect { request ->
                request.write(req.toString())
                val conn = request.connection as HttpURLConnection
                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                responseCode to stream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
            }

        val resp = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            ?: return "引擎响应无法解析(HTTP $code)：${body.take(200)}"
        resp.get("error")?.let { return it.asString }
        if (resp.get("status")?.asString != "success") {
            return "引擎响应异常(HTTP $code)：${body.take(200)}"
        }
        return null
    }
}
