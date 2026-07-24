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

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 独立设置页 "Lua XML Defs"（项目级）。
 *
 * 提供 XML 定义目录选择。应用后写入 [LuaXmlSettings]，重建由
 * [LuaXmlStartupActivity] 订阅 TOPIC 后防抖触发。
 */
class LuaXmlConfigurable(private val project: Project) : Configurable {

    private var rootPanel: JPanel? = null
    private val dirField = TextFieldWithBrowseButton()

    override fun getDisplayName(): String = "Lua XML Defs"

    override fun createComponent(): JComponent {
        dirField.addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            val chosen = FileChooser.chooseFile(descriptor, project, null)
            if (chosen != null) {
                dirField.text = chosen.path
            }
        }
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("XML 定义目录:", dirField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        rootPanel = panel
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val settings = LuaXmlSettings.getInstance(project)
        return dirField.text.trim() != settings.xmlDefDir
    }

    override fun apply() {
        val settings = LuaXmlSettings.getInstance(project)
        settings.xmlDefDir = dirField.text.trim()
    }

    override fun reset() {
        val settings = LuaXmlSettings.getInstance(project)
        dirField.text = settings.xmlDefDir
    }
}
