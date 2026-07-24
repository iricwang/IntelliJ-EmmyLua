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

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * 项目级配置：UE 工程目录（用于匹配引擎推送的 X-UE-Project）。空串表示自动探测
 * （从 IDEA 工程根向上查找含 *.uproject 的目录）。
 *
 * 与 [com.tang.intellij.lua.xml.LuaXmlSettings] 共用 emmy.xml 存储文件（State 名不同）。
 */
@Service(Service.Level.PROJECT)
@State(name = "LuaUEBlueprintSettings", storages = [(Storage("emmy.xml"))])
class LuaUEBlueprintSettings(private val project: Project) : PersistentStateComponent<LuaUEBlueprintSettings.State> {

    companion object {
        fun getInstance(project: Project): LuaUEBlueprintSettings =
            project.getService(LuaUEBlueprintSettings::class.java)
    }

    private var state = State()

    /** UE 工程根目录（本地绝对路径）。空串 = 自动探测。 */
    var ueProjectDir: String
        get() = state.ueProjectDir
        set(value) {
            if (state.ueProjectDir != value) {
                state.ueProjectDir = value
                project.scheduleSave()
            }
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    class State {
        var ueProjectDir: String = ""
    }
}
