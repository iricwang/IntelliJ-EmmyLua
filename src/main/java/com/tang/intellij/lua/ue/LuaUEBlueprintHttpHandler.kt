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

import com.intellij.openapi.project.ProjectManager
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService

/**
 * UE 蓝图注解接收端：`POST /api/ue-defs`（IDE 内建 Web Server，每实例独立端口，
 * 端口经 IDE 注册表文件告知引擎侧）。
 *
 * 请求约定：
 * - `X-UE-Project`: UE 工程根目录（必填，广播自过滤用；多开 IDE 各自匹配）
 * - `X-Def-Op`: `upsert`（默认）| `delete` | `clear` | `begin-batch` | `end-batch`
 * - `X-Def-Path`: 相对路径（如 `GamePlay/WBP_BagItem.lua`，upsert/delete 必填）
 * - body: 注解文本（UTF-8，upsert 时读取）
 *
 * 批量推送（一次编译几百个 WBP）用 begin-batch/end-batch 包裹，IDE 合并为一次 VFS 刷新。
 * [execute] 返回 null 表示 200 OK，返回字符串作为错误描述发给调用方。
 */
class LuaUEBlueprintHttpHandler : RestService() {

    override fun getServiceName(): String = "ue-defs"

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ): String? {
        val headers = request.headers()
        val ueProject = headers.get("X-UE-Project")?.trim().orEmpty()
        if (ueProject.isEmpty()) return "missing X-UE-Project header"

        val manager = ProjectManager.getInstance().openProjects
            .asSequence()
            .map { LuaUEBlueprintManager.getInstance(it) }
            .firstOrNull { it.matches(ueProject) }
            ?: return "no matching project for: $ueProject"

        val op = headers.get("X-Def-Op")?.trim()?.lowercase().orEmpty().ifEmpty { "upsert" }
        return when (op) {
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
    }
}
