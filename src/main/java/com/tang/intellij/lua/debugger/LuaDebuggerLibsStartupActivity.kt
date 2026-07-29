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

package com.tang.intellij.lua.debugger

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 工程打开时自动把调试运行库释放到用户目录（见 [LuaDebuggerLibs]），
 * 无需先启动一次调试会话。
 */
class LuaDebuggerLibsStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        withContext(Dispatchers.IO) {
            LuaDebuggerLibs.ensureDeployed()
        }
    }
}
