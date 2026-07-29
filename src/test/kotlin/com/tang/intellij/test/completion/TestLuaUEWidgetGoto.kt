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

package com.tang.intellij.test.completion

import com.intellij.psi.PsiNamedElement
import com.tang.intellij.lua.ue.LuaUEWidgetGotoDeclarationHandler
import com.tang.intellij.test.LuaTestBase

/**
 * 验证 [LuaUEWidgetGotoDeclarationHandler]：界面加载调用的 URL 字符串参数
 * 在 ctrl+click 时给出「在引擎中打开 WBP」的导航目标。
 */
class TestLuaUEWidgetGoto : LuaTestBase() {

    private fun gotoTargets() = LuaUEWidgetGotoDeclarationHandler().getGotoDeclarationTargets(
        myFixture.file.findElementAt(myFixture.caretOffset), myFixture.caretOffset, myFixture.editor
    )

    /** 命中配置函数：应返回单个引擎打开目标，名字与资产路径正确。 */
    fun testGotoEngineFromCreateViewByUrl() {
        myFixture.configureByText(
            "test.lua",
            "local ctx\nctx:createViewByUrl(\"Common/WBP_UI_MoneyList_<caret>Item\")"
        )
        val targets = gotoTargets()
        assertNotNull("应返回引擎打开目标", targets)
        assertEquals(1, targets!!.size)
        assertEquals("WBP_UI_MoneyList_Item", (targets[0] as PsiNamedElement).name)
        assertTrue((targets[0] as com.intellij.navigation.NavigationItem).canNavigate())
    }

    /** 冒号与点调用、全局函数形态都应识别。 */
    fun testCallForms() {
        myFixture.configureByText(
            "test.lua",
            "newWidget(\"Panel/Bag/WBP_Bag<caret>Main\")"
        )
        assertNotNull(gotoTargets())
    }

    /** 未配置的函数不接管。 */
    fun testNoInterceptForUnconfiguredFunction() {
        myFixture.configureByText(
            "test.lua",
            "local ctx\nctx:loadAssetAsync(\"Common/WBP_<caret>X\", print)"
        )
        assertNull(gotoTargets())
    }

    /** 光标不在字符串参数上（函数名位置）不接管。 */
    fun testNoInterceptOnFunctionName() {
        myFixture.configureByText(
            "test.lua",
            "local ctx\nctx:createView<caret>ByUrl(\"Common/WBP_X\")"
        )
        assertNull(gotoTargets())
    }

    /** 纯函数：URL → 资产路径（对齐 Context.lua parseViewUrlToPath）。 */
    fun testUrlToAssetPath() {
        val rules = listOf("Common" to "Common", "*" to "Panel")
        val tpl = "/Game/Res/SGUI/{folder}/{path}/{name}.{name}"
        // Common 开头：前置 Common/（与引擎行为一致，形成 Common/Common 双段）
        assertEquals(
            "/Game/Res/SGUI/Common/Common/WBP_UI_MoneyList_Item.WBP_UI_MoneyList_Item",
            LuaUEWidgetGotoDeclarationHandler.urlToAssetPath("Common/WBP_UI_MoneyList_Item", rules, tpl)
        )
        // 三段 Common URL：取末三段
        assertEquals(
            "/Game/Res/SGUI/Common/Confirm/WBP_Confirm.WBP_Confirm",
            LuaUEWidgetGotoDeclarationHandler.urlToAssetPath("Common/Confirm/WBP_Confirm", rules, tpl)
        )
        // 兜底前缀 Panel
        assertEquals(
            "/Game/Res/SGUI/Panel/Bag/WBP_BagMain.WBP_BagMain",
            LuaUEWidgetGotoDeclarationHandler.urlToAssetPath("Bag/WBP_BagMain", rules, tpl)
        )
        // 段数不足 / 无兜底规则 → null
        assertNull(LuaUEWidgetGotoDeclarationHandler.urlToAssetPath("WBP_X", rules, tpl))
        assertNull(LuaUEWidgetGotoDeclarationHandler.urlToAssetPath("Bag/WBP_X", listOf("Common" to "Common"), tpl))
        assertNull(LuaUEWidgetGotoDeclarationHandler.urlToAssetPath("", rules, tpl))
    }
}
