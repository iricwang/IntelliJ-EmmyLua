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

package com.tang.intellij.lua.psi.search

import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.tang.intellij.lua.ty.LuaClassFactory

/**
 * 类工厂虚拟类的类名来源：把 ClassActivity/ClassDialog/ClassToast 注册的类名
 * （如 `shelter_mainActivity`、`lobbynpctask_dialog`）加入类名枚举，
 * 使 `---@return xxx` 等 doc 类型位置能补全出这些没有 `---@class` 定义的"虚拟类"。
 *
 * 经 `com.tang.intellij.lua.luaShortNamesManager` EP 注册，由
 * [CompositeLuaShortNamesManager] 聚合（doc 补全走 processClassNames → 各 EP）。
 */
class LuaClassFactoryShortNamesManager : LuaShortNamesManager() {

    override fun processAllClassNames(project: Project, processor: Processor<String>): Boolean {
        return LuaClassFactory.processRegisteredClasses(project, processor)
    }
}
