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

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VfsUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService

/**
 * 接收引擎推送的 Lua 脚本路径并在已运行 IDE 里打开（`POST /api/open-lua-file`）。
 *
 * 引擎 WBP 编辑器点「打开lua脚本」时，经 [UnLangService] 的 FEmmyLuaClient 把脚本绝对路径
 * 推送给所有在线 IDE，IDE 按 `X-UE-Project` 自匹配后用 FileEditorManager 打开文件。
 *
 * 协议：
 * - `X-UE-Project`: UE 工程根目录（必填，广播自过滤；复用 [LuaUEBlueprintManager.matches]）
 * - body: Lua 脚本绝对路径（UTF-8，如 `X:/.../Source/LuaScripts/client/gameplay/WBP_BagItem.lua`）
 */
class LuaUEOpenLuaFileHandler : RestService() {

    override fun getServiceName(): String = "open-lua-file"

    override fun isMethodSupported(method: HttpMethod): Boolean = method == HttpMethod.POST

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ): String? {
        val headers = request.headers()
        val ueProject = headers.get("X-UE-Project")?.trim().orEmpty()
        if (ueProject.isEmpty()) return "missing X-UE-Project header"

        val project = ProjectManager.getInstance().openProjects
            .firstOrNull { LuaUEBlueprintManager.getInstance(it).matches(ueProject) }
            ?: return "no matching project for: $ueProject"

        val scriptPath = request.content().toString(Charsets.UTF_8).trim()
        if (scriptPath.isEmpty()) return "empty script path in body"

        // 路径归属校验：body 来自本机 HTTP，必须限制在本工程根目录内，避免被诱导打开任意文件。
        val canonical = try {
            java.io.File(scriptPath).canonicalFile
        } catch (e: java.io.IOException) {
            return "invalid script path: $scriptPath"
        }
        val basePath = project.basePath ?: return "project has no base path"
        val baseDir = java.io.File(basePath).canonicalFile
        if (!com.intellij.openapi.util.io.FileUtil.isAncestor(baseDir, canonical, false)) {
            return "script path outside project: $scriptPath"
        }

        val vf = VfsUtil.findFileByIoFile(canonical, true)
            ?: return "script file not found: $scriptPath"

        // openFile 必须在 EDT；execute 跑在内建服务器 Netty IO 线程。
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
        org.jetbrains.ide.RestService.sendOk(request, context)
        return null
    }
}
