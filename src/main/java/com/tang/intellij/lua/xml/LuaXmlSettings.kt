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

package com.tang.intellij.lua.xml

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/**
 * 项目级配置：XML 类型定义目录。
 *
 * 独立于 EmmyLua 既有的 [com.tang.intellij.lua.project.LuaSettings]（application 级），
 * 以项目为单位保存 XML 定义目录。
 */
@Service(Service.Level.PROJECT)
@State(name = "LuaXmlSettings", storages = [(Storage("emmy.xml"))])
class LuaXmlSettings(private val project: Project) : PersistentStateComponent<LuaXmlSettings.State> {

    companion object {
        /** 配置变更广播；订阅者据此触发重新解析/刷新。 */
        val TOPIC: Topic<LuaXmlSettingsListener> =
            Topic.create("lua xml def settings changes", LuaXmlSettingsListener::class.java)

        fun getInstance(project: Project): LuaXmlSettings =
            project.getService(LuaXmlSettings::class.java)
    }

    private var state = State()

    /** XML 定义文件根目录（本地绝对路径）。空串表示未配置。 */
    var xmlDefDir: String
        get() = state.xmlDefDir
        set(value) {
            if (state.xmlDefDir != value) {
                state.xmlDefDir = value
                fireChanged()
            }
        }

    /**
     * 单个打包 bundle 允许的最大定义条目数。
     * 已废弃：分包改为按体积（LuaXmlManager.MAX_PART_BYTES，适配 IDE 索引文件大小限制），
     * 该值仅为兼容旧配置保留，不再被读取。
     */
    @Deprecated("分包已改为按体积，不再按条目数")
    var maxDefsPerBundle: Int
        get() = state.maxDefsPerBundle
        set(value) {
            if (state.maxDefsPerBundle != value) {
                state.maxDefsPerBundle = value
                fireChanged()
            }
        }

    private fun fireChanged() {
        project.messageBus.syncPublisher(TOPIC).onChanged()
        project.scheduleSave()
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    class State {
        var xmlDefDir: String = ""
        var maxDefsPerBundle: Int = 500
    }
}

interface LuaXmlSettingsListener {
    fun onChanged()
}
