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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * 手动刷新反射类型注解（Tools 菜单）：按设置页的类型列表重新向引擎拉取。
 */
class LuaUERefreshReflectAction : AnAction("刷新 UE 反射类型注解") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LuaUEReflectManager.getInstance(project).refresh(notifyOnFailure = true)
    }
}
