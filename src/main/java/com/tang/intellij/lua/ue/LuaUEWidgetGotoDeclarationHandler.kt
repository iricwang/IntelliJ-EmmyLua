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

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.util.parentOfType
import com.tang.intellij.lua.lang.LuaLanguage
import com.tang.intellij.lua.psi.LuaCallExpr
import com.tang.intellij.lua.psi.LuaIndexExpr
import com.tang.intellij.lua.psi.LuaLiteralExpr
import com.tang.intellij.lua.psi.LuaLiteralKind
import com.tang.intellij.lua.psi.LuaNameExpr
import com.tang.intellij.lua.psi.kind
import com.tang.intellij.lua.psi.stringValue
import javax.swing.Icon

/**
 * 界面资产跳转：ctrl+click 界面加载调用（如 `ctx:createViewByUrl("Common/WBP_X")`）
 * 的字符串参数时，把 URL 映射为引擎资产对象路径，经 [LuaUEWidgetOpener] 让引擎
 * 编辑器打开对应 WBP——体验等同"跳转到定义"，只是目标在 UE 编辑器里。
 *
 * 识别的函数名、URL 前缀规则、资产路径模板均可在设置页配置
 * （[LuaUEBlueprintSettings]，默认复刻 Context.lua parseViewUrlToPath：
 * `Common` 开头前置 `Common/`，其余前置 `Panel/`，
 * 再拼 `/Game/Res/SGUI/{folder}/{path}/{name}.{name}`）。
 *
 * 未命中配置（普通函数/非字符串参数/URL 无法映射）返回 null，不影响既有跳转。
 */
class LuaUEWidgetGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val literal = sourceElement?.parent as? LuaLiteralExpr ?: return null
        if (literal.kind != LuaLiteralKind.String) return null
        val callExpr = literal.parentOfType<LuaCallExpr>() ?: return null
        val funcName = calledName(callExpr) ?: return null

        val settings = LuaUEBlueprintSettings.getInstance(literal.project)
        if (funcName !in settings.widgetGotoFunctionSet()) return null

        val assetPath = urlToAssetPath(
            literal.stringValue,
            settings.widgetUrlPrefixRules(),
            settings.widgetUrlTemplate
        ) ?: return null

        return arrayOf(UeWidgetNavElement(literal, assetPath))
    }

    /** 取被调函数名：`a:b()` / `a.b()` 取索引名，`f()` 取名字表达式。 */
    private fun calledName(callExpr: LuaCallExpr): String? = when (val expr = callExpr.expr) {
        is LuaIndexExpr -> expr.name
        is LuaNameExpr -> expr.name
        else -> null
    }

    /**
     * 伪导航元素：不作为真实跳转目标，navigate() 触发引擎打开资产。
     * 实现 [PsiNamedElement]/[ItemPresentation] 让"选择目标"弹窗正常显示名字与图标。
     */
    private class UeWidgetNavElement(
        private val anchor: LuaLiteralExpr,
        private val assetPath: String,
    ) : LightElement(anchor.manager, LuaLanguage.INSTANCE), PsiNamedElement, ItemPresentation {

        override fun getText(): String = assetPath
        override fun toString(): String = "UeWidgetNavElement($assetPath)"
        override fun getContainingFile() = anchor.containingFile

        override fun getName(): String = assetPath.substringAfterLast('.')
        override fun setName(name: String): PsiElement = this

        override fun navigate(requestFocus: Boolean) {
            LuaUEWidgetOpener.openAsset(anchor.project, assetPath)
        }

        override fun canNavigate(): Boolean = true
        override fun canNavigateToSource(): Boolean = false

        override fun getPresentation(): ItemPresentation = this
        override fun getPresentableText(): String = name
        override fun getLocationString(): String = "UE: $assetPath"
        override fun getIcon(unused: Boolean): Icon = AllIcons.FileTypes.UiForm
    }

    companion object {
        /**
         * URL → 资产对象路径。先按前缀规则补前缀，再取末三段（folder/path/name）套模板；
         * 规则未命中或不足三段返回 null。
         */
        internal fun urlToAssetPath(
            url: String,
            rules: List<Pair<String, String>>,
            template: String,
        ): String? {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return null
            val prefix = rules.firstOrNull { it.first != "*" && trimmed.startsWith(it.first) }?.second
                ?: rules.firstOrNull { it.first == "*" }?.second
                ?: return null
            val full = "$prefix/$trimmed"
            val m = Regex("([^/]+)/([^/]+)/([^/]+)$").find(full) ?: return null
            val (folder, path, name) = m.destructured
            return template
                .replace("{folder}", folder)
                .replace("{path}", path)
                .replace("{name}", name)
        }
    }
}
