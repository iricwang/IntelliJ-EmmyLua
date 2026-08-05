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

    /** UnLangService IDE 服务端点端口（引擎编辑器内监听，提供 /api/open-asset）。 */
    var ueBridgePort: Int
        get() = state.ueBridgePort
        set(value) {
            if (state.ueBridgePort != value) {
                state.ueBridgePort = value
                project.scheduleSave()
            }
        }

    /**
     * 界面加载函数名（分号分隔）。这些函数调用上的字符串参数会被识别为界面 URL，
     * ctrl+click 时经引擎 open-asset 打开对应 WBP。
     */
    var widgetGotoFunctions: String
        get() = state.widgetGotoFunctions
        set(value) {
            if (state.widgetGotoFunctions != value) {
                state.widgetGotoFunctions = value
                project.scheduleSave()
            }
        }

    /** 界面 URL → 资产对象路径模板，占位符 {folder}/{path}/{name}。 */
    var widgetUrlTemplate: String
        get() = state.widgetUrlTemplate
        set(value) {
            if (state.widgetUrlTemplate != value) {
                state.widgetUrlTemplate = value
                project.scheduleSave()
            }
        }

    /**
     * URL 前缀规则（分号分隔的 键=值）：URL 以「键」开头时前置「值/」，`*` 为兜底。
     * 复刻 Context.lua parseViewUrlToPath：Common 开头前置 Common/，其余前置 Panel/。
     */
    var widgetUrlPrefixes: String
        get() = state.widgetUrlPrefixes
        set(value) {
            if (state.widgetUrlPrefixes != value) {
                state.widgetUrlPrefixes = value
                project.scheduleSave()
            }
        }

    /**
     * MVVM 绑定字段导出配置（DelegateExports.txt）的路径。空串 = 默认取
     * `<工程根>/DelegateExports.txt`。
     */
    var delegateExportsPath: String
        get() = state.delegateExportsPath
        set(value) {
            if (state.delegateExportsPath != value) {
                state.delegateExportsPath = value
                project.scheduleSave()
            }
        }

    /** 解析生效的 DelegateExports.txt 路径。 */
    fun resolveDelegateExportsFile(): java.io.File =
        delegateExportsPath.trim().takeIf { it.isNotEmpty() }?.let { java.io.File(it) }
            ?: java.io.File(project.basePath ?: "", "DelegateExports.txt")

    /** 解析后的函数名集合。 */
    fun widgetGotoFunctionSet(): Set<String> =
        widgetGotoFunctions.split(';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** 解析后的前缀规则（保持配置顺序，`*` 兜底）。 */
    fun widgetUrlPrefixRules(): List<Pair<String, String>> =
        widgetUrlPrefixes.split(';').mapNotNull { rule ->
            val idx = rule.indexOf('=')
            if (idx <= 0) null
            else rule.substring(0, idx).trim() to rule.substring(idx + 1).trim()
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    class State {
        var ueProjectDir: String = ""
        var ueBridgePort: Int = 13888
        var widgetGotoFunctions: String = "newWidget;newWidgetAsync;createWidgetAsync;createViewByUrl"
        var widgetUrlTemplate: String = "/Game/Res/SGUI/{folder}/{path}/{name}.{name}"
        var widgetUrlPrefixes: String = "Common=Common;*=Panel"
        var delegateExportsPath: String = ""
    }
}
