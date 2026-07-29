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

import com.intellij.icons.AllIcons
import com.intellij.ide.IconProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlToken
import javax.swing.Icon

/**
 * RPC 跳转弹窗的图标装饰：[LuaXmlGotoDeclarationHandler] 返回的 XML 目标是标签名
 * 叶子 token（[XmlToken]），自身没有图标——给它们补 XML 文件图标，与 Lua 方法
 * 目标自带的 M 图标（[com.tang.intellij.lua.editor.LuaIconProvider] 提供）呼应。
 *
 * 只装饰配置的 XML 定义目录（[LuaXmlSettings.xmlDefDir]）下的文件，避免影响
 * 工程里其它普通 xml 的显示。
 */
class LuaXmlRpcIconProvider : IconProvider() {

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element !is XmlToken) return null
        val vf = element.containingFile?.virtualFile ?: return null
        if (vf.extension?.equals("xml", ignoreCase = true) != true) return null

        val defDir = LuaXmlSettings.getInstance(element.project).xmlDefDir.trim()
            .takeIf { it.isNotEmpty() } ?: return null
        if (!FileUtil.isAncestor(FileUtil.toSystemIndependentName(defDir), vf.path, true)) return null

        return AllIcons.FileTypes.Xml
    }
}
