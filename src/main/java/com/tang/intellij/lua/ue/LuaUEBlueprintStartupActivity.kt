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

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * 启动装配：
 * 1. 初始化挂载状态并把缓存目录刷进 VFS（引擎在 IDE 关闭期间推送/落盘的改动也能被索引）。
 * 2. 写 IDE 注册表文件（引擎侧据此发现本 IDE；工程关闭时由 [LuaUEBlueprintManager.dispose] 删除）。
 */
class LuaUEBlueprintStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val manager = LuaUEBlueprintManager.getInstance(project)
        manager.initMounted()
        manager.registerIde()
        manager.refreshNow()
    }
}
