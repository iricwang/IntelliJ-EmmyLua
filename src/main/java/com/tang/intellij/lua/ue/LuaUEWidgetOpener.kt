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
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.net.HttpURLConnection

/**
 * IDE → 引擎调用器：经 UnLangService 插件内建的 IDE 服务端点
 * （`POST 127.0.0.1:{port}/api/open-asset`，见引擎侧 EmmyLuaIdeServer）让引擎
 * 编辑器打开指定资产（WBP 等）。独立于 UEAIBridge，不依赖其他插件。
 *
 * 请求异步发出，失败（引擎未启动 / 资产不存在）以通知形式反馈，不阻塞 UI。
 */
object LuaUEWidgetOpener {

    private val LOG = Logger.getInstance(LuaUEWidgetOpener::class.java)
    private const val NOTIFICATION_GROUP = "Lua UE"

    /** 通用告警通知（"Lua UE" 通知组）。 */
    fun notifyWarning(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(title, content, NotificationType.WARNING)
            .notify(project)
    }

    fun openAsset(project: Project, assetPath: String) {
        val port = LuaUEBlueprintSettings.getInstance(project).ueBridgePort
        ApplicationManager.getApplication().executeOnPooledThread {
            val error = runCatching { callOpenAsset(port, assetPath) }
                .getOrElse { "连接引擎失败(127.0.0.1:$port)：${it.message}" }
            if (error != null) {
                LOG.info("open-asset 失败: $error")
                NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification("打开引擎资产失败", error, NotificationType.WARNING)
                    .notify(project)
            }else{
                NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification("打开引擎界面资产", "打开界面:$assetPath", NotificationType.INFORMATION)
                    .notify(project)
            }
        }
    }

    /** @return null 表示成功；否则为错误描述。 */
    private fun callOpenAsset(port: Int, assetPath: String): String? {
        val req = JsonObject().apply { addProperty("asset_path", assetPath) }

        val (code, body) = HttpRequests.post("http://127.0.0.1:$port/api/open-asset", "application/json")
            .connectTimeout(3500)
            .readTimeout(20000) // 资产加载可能较慢（引擎侧游戏线程带 10s 超时兜底）
            .connect { request ->
                request.write(req.toString())
                val conn = request.connection as HttpURLConnection
                val responseCode = conn.responseCode
                // 非 2xx 时错误描述在 errorStream 的 JSON body 里
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
