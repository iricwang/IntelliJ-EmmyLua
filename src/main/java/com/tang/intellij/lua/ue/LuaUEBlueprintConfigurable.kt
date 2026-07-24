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

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 独立设置页 "Lua UE Blueprint Defs"（项目级）。
 *
 * 提供 UE 工程目录手动覆盖（留空自动探测），并实时显示自动探测结果与缓存目录位置。
 * 应用后重写 IDE 注册表文件（引擎侧按此匹配推送目标）。
 */
class LuaUEBlueprintConfigurable(private val project: Project) : Configurable {

    private var rootPanel: JPanel? = null
    private val dirField = TextFieldWithBrowseButton()
    private val detectedLabel = JBLabel()

    override fun getDisplayName(): String = "Lua UE Blueprint Defs"

    override fun createComponent(): JComponent {
        dirField.addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            val chosen = FileChooser.chooseFile(descriptor, project, null)
            if (chosen != null) {
                dirField.text = chosen.path
            }
        }
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("UE 工程目录(留空自动探测):", dirField)
            .addLabeledComponent("当前生效目录:", detectedLabel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        rootPanel = panel
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val settings = LuaUEBlueprintSettings.getInstance(project)
        return dirField.text.trim() != settings.ueProjectDir
    }

    override fun apply() {
        val settings = LuaUEBlueprintSettings.getInstance(project)
        settings.ueProjectDir = dirField.text.trim()
        // 注册表文件包含生效目录，重写让引擎侧立即感知。
        LuaUEBlueprintManager.getInstance(project).registerIde()
        updateDetectedLabel()
    }

    override fun reset() {
        val settings = LuaUEBlueprintSettings.getInstance(project)
        dirField.text = settings.ueProjectDir
        updateDetectedLabel()
    }

    private fun updateDetectedLabel() {
        val manager = LuaUEBlueprintManager.getInstance(project)
        val resolved = manager.resolveUeProjectDir()
        detectedLabel.text = if (resolved.isNotEmpty()) resolved else "(未探测到 *.uproject，将使用前缀匹配兜底)"
    }
}
