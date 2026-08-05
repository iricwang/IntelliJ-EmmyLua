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
 * 手动触发引擎重新执行 UnLua Generate IntelliSense 并推送注解（Tools 菜单）。
 *
 * 请求 UnLangService `POST 127.0.0.1:{ueBridgePort}/api/generate-intellisense`，
 * 引擎 Generate 完成后经 [/api/unla-defs][LuaUnLuaDefHttpHandler] 批量推送回 IDE。
 * action 只负责触发，推送由引擎异步完成。
 */
class LuaUnLuaRefreshAction : AnAction("刷新 UnLua IntelliSense 注解") {

    companion object {
        private val LOG = Logger.getInstance(LuaUnLuaRefreshAction::class.java)
        private const val READ_TIMEOUT_MS = 120000 // Generate 反射多个 UCLASS 较慢
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val port = LuaUEBlueprintSettings.getInstance(project).ueBridgePort
        ApplicationManager.getApplication().executeOnPooledThread {
            val error = runCatching { requestGenerate(port) }
                .getOrElse { "连接引擎失败(127.0.0.1:$port)：${it.message}" }
            if (error != null) {
                LOG.info("generate-intellisense 失败: $error")
                LuaUEWidgetOpener.notifyWarning(project, "刷新 UnLua IntelliSense 失败", error)
            } else {
                LuaUEWidgetOpener.notifyWarning(project, "刷新 UnLua IntelliSense", "已请求引擎重新生成，注解将自动推送回 IDE")
            }
        }
    }

    /** @return null 表示成功；否则为错误描述。 */
    private fun requestGenerate(port: Int): String? {
        val (code, body) = HttpRequests.post("http://127.0.0.1:$port/api/generate-intellisense", "application/json")
            .connectTimeout(3500)
            .readTimeout(READ_TIMEOUT_MS)
            .connect { request ->
                request.write(JsonObject().toString())
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
