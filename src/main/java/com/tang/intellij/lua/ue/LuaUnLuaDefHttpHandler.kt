/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the License");
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

import com.intellij.openapi.project.ProjectManager
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService

/**
 * UnLua IntelliSense 注解接收端：`POST /api/unla-defs`（IDE 内建 Web Server）。
 *
 * 协议与 [LuaUEBlueprintHttpHandler] 一致：
 * - `X-UE-Project`: UE 工程根目录（必填，广播自过滤；复用 [LuaUEBlueprintManager.matches]）
 * - `X-Def-Op`: `upsert`（默认）| `delete` | `clear` | `begin-batch` | `end-batch`
 * - `X-Def-Path`: 相对路径（upsert/delete 必填）
 * - body: 注解文本（UTF-8，upsert 时读取）
 *
 * 返回 null 表示 200 OK，返回字符串作为错误描述发给调用方。
 */
class LuaUnLuaDefHttpHandler : RestService() {

    override fun getServiceName(): String = "unla-defs"

    override fun isMethodSupported(method: HttpMethod): Boolean = method == HttpMethod.POST

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ): String? {
        val headers = request.headers()
        val ueProject = headers.get("X-UE-Project")?.trim().orEmpty()
        if (ueProject.isEmpty()) return "missing X-UE-Project header"

        val matchedProject = ProjectManager.getInstance().openProjects
            .firstOrNull { LuaUEBlueprintManager.getInstance(it).matches(ueProject) }
            ?: return "no matching project for: $ueProject"
        val manager = LuaUnLuaDefManager.getInstance(matchedProject)

        val op = headers.get("X-Def-Op")?.trim()?.lowercase().orEmpty().ifEmpty { "upsert" }
        val result = when (op) {
            "upsert" -> {
                val path = headers.get("X-Def-Path")?.trim().orEmpty()
                if (path.isEmpty()) return "missing X-Def-Path header"
                val body = request.content().toString(Charsets.UTF_8)
                if (manager.upsertDef(path, body)) null else "invalid X-Def-Path: $path"
            }
            "delete" -> {
                val path = headers.get("X-Def-Path")?.trim().orEmpty()
                if (path.isEmpty()) return "missing X-Def-Path header"
                manager.deleteDef(path)
                null
            }
            "clear" -> {
                manager.clearDefs()
                null
            }
            "begin-batch" -> {
                manager.beginBatch()
                null
            }
            "end-batch" -> {
                manager.endBatch()
                null
            }
            else -> "unknown X-Def-Op: $op"
        }
        if (result == null) {
            org.jetbrains.ide.RestService.sendOk(request, context)
        }
        return result
    }
}
