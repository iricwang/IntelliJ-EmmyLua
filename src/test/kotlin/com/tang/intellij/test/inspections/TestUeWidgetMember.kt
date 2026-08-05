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

package com.tang.intellij.test.inspections

import com.tang.intellij.lua.codeInsight.inspection.UeWidgetMemberInspection
import com.tang.intellij.test.LuaTestBase

/**
 * [UeWidgetMemberInspection]：蓝图界面类（WBP_* / ue-defs 缓存定义）的成员访问，
 * 组件字段或函数不存在（含完整父类链判定）时报 ERROR。
 */
class TestUeWidgetMember : LuaTestBase() {

    companion object {
        private const val WBP_DEF = """
            ---@class WBP_UI_ShopM12_BuyPanel
            ---@field Title table
            ---@field BtnClose table
            local WBP_UI_ShopM12_BuyPanel = {}
        """
    }

    private fun highlight(wbpDef: String?, code: String) = myFixture.run {
        if (wbpDef != null) {
            addFileToProject("client/game_lobby/guis/mall/wbp_defs.lua", wbpDef.trimIndent())
        }
        addFileToProject("client/game_lobby/guis/mall/caller.lua", code.trimIndent())
            .also { configureFromExistingVirtualFile(it.virtualFile) }
        enableInspections(UeWidgetMemberInspection())
        doHighlighting()
    }

    /** 不存在的组件报 ERROR，存在的组件不报（Defines 内 view.X 场景） */
    fun `test unknown widget flagged`() {
        val highlights = highlight(WBP_DEF, """
            local M = ClassViewModel("ShopBuyPanelModel")

            ---@param view WBP_UI_ShopM12_BuyPanel
            function M:Defines(view, fields, delegates)
                self.title = FIELD(fields.Text.Text, view.Title)
                self.x = FIELD(fields.Text.Text, view.Root)
            end

            return M
        """)
        val errors = highlights.filter { it.description?.contains("不存在") == true }
        assertEquals(1, errors.size)
        assertTrue(errors.first().description!!.contains("组件不存在"))
        assertTrue(errors.first().description!!.contains("Root"))
    }

    /** 冒号调用不存在的函数也报（函数不存在），存在的函数不报 */
    fun `test unknown colon function flagged`() {
        val highlights = highlight("""
            ---@class WBP_UI_Base
            ---@field Title table
            local WBP_UI_Base = {}
            function WBP_UI_Base:Show() end

            ---@class WBP_UI_ShopM12_BuyPanel : WBP_UI_Base
            local WBP_UI_ShopM12_BuyPanel = {}
        """, """
            ---@param view WBP_UI_ShopM12_BuyPanel
            local function bind(view)
                view:Show()
                view:DoSomething()
            end
        """)
        val errors = highlights.filter { it.description?.contains("不存在") == true }
        assertEquals(1, errors.size)
        assertTrue(errors.first().description!!.contains("函数不存在"))
        assertTrue(errors.first().description!!.contains("DoSomething"))
    }

    /** 赋值写入（动态挂字段）不报 */
    fun `test write access not flagged`() {
        val highlights = highlight(WBP_DEF, """
            ---@param view WBP_UI_ShopM12_BuyPanel
            local function bind(view)
                view.CustomFlag = 1
            end
        """)
        assertTrue("不应报不存在，实际：${highlights.map { it.description }}", highlights.none { it.description?.contains("不存在") == true })
    }

    /** 蓝图类未定义（注解未推送）时不报，防误报 */
    fun `test undefined class not flagged`() {
        val highlights = highlight(null, """
            ---@param view WBP_UI_ShopM12_BuyPanel
            local function bind(view)
                local x = view.Root
            end
        """)
        assertTrue("不应报不存在，实际：${highlights.map { it.description }}", highlights.none { it.description?.contains("不存在") == true })
    }

    /** 冒号函数：父类链有未定义环节（引擎基类无注解）时不报 */
    fun `test incomplete super chain colon call not flagged`() {
        val highlights = highlight("""
            ---@class WBP_UI_ShopM12_BuyPanel : UUserWidget
            ---@field Title table
            local WBP_UI_ShopM12_BuyPanel = {}
        """, """
            ---@param view WBP_UI_ShopM12_BuyPanel
            local function bind(view)
                view:DoSomething()
            end
        """)
        assertTrue("不应报不存在，实际：${highlights.map { it.description }}", highlights.none { it.description?.contains("不存在") == true })
    }

    /** 点访问组件：父类链不完整也照样报（组件只来自自身 widget 树） */
    fun `test dot access flagged despite incomplete chain`() {
        val highlights = highlight("""
            ---@class WBP_UI_ShopM12_BuyPanel : UView
            ---@field Title table
            local WBP_UI_ShopM12_BuyPanel = {}
        """, """
            ---@param view WBP_UI_ShopM12_BuyPanel
            local function bind(view)
                local x = view.Root
            end
        """)
        val errors = highlights.filter { it.description?.contains("不存在") == true }
        assertEquals("应报组件不存在，实际：${highlights.map { it.description }}", 1, errors.size)
        assertTrue(errors.first().description!!.contains("Root"))
    }

    /** self.View.Root（View 属性类型来自 Defines 的 @param view）：Root 缺失应报 */
    fun `test self view member flagged`() {
        val highlights = highlight("""
            ---@class WBP_UI_ShopM12_BuyPanel
            ---@field Title table
            ---@field FacilityList table
            local WBP_UI_ShopM12_BuyPanel = {}
        """, """
            local M = ClassViewModel("ShopBuyPanelModel")

            ---@param view WBP_UI_ShopM12_BuyPanel
            function M:Defines(view, fields, delegates)
                self.View.FacilityList = view.FacilityList
            end

            function M:foo()
                local v = self.View.Root
            end
        """)
        val errors = highlights.filter { it.description?.contains("不存在") == true }
        assertEquals("self.View.Root 应报组件不存在，实际：${highlights.map { it.description }}", 1, errors.size)
        assertTrue(errors.first().description!!.contains("Root"))
    }

    /** self.View.Root:Change()：Root（点访问缺失）应报；链完整时 Change（冒号缺失）也报 */
    fun `test self view root colon call flagged`() {
        val highlights = highlight("""
            ---@class WBP_UI_ShopM12_BuyPanel
            ---@field Title table
            ---@field FacilityList table
            local WBP_UI_ShopM12_BuyPanel = {}
        """, """
            local M = ClassViewModel("ShopBuyPanelModel")

            ---@param view WBP_UI_ShopM12_BuyPanel
            function M:Defines(view, fields, delegates)
                self.View.FacilityList = view.FacilityList
            end

            function M:foo()
                self.View.Root:Change()
            end
        """)
        val errors = highlights.filter { it.description?.contains("不存在") == true }
        assertTrue("Root 应报组件不存在，实际：${highlights.map { it.description }}",
            errors.any { it.description!!.contains("\"Root\"") })
    }

    /** 成员在父类上存在则不报 */
    fun `test member from super class ok`() {
        val highlights = highlight("""
            ---@class UUserWidget
            ---@field Root table
            local UUserWidget = {}

            ---@class WBP_UI_ShopM12_BuyPanel : UUserWidget
            ---@field Title table
            local WBP_UI_ShopM12_BuyPanel = {}
        """, """
            ---@param view WBP_UI_ShopM12_BuyPanel
            local function bind(view)
                local x = view.Root
            end
        """)
        assertTrue("不应报不存在，实际：${highlights.map { it.description }}", highlights.none { it.description?.contains("不存在") == true })
    }

    /** union 类型：任一成分类有该成员即视为存在 */
    fun `test union view type`() {
        val highlights = highlight("""
            ---@class WBP_A
            ---@field Title table
            local WBP_A = {}

            ---@class WBP_B
            ---@field BtnClose table
            local WBP_B = {}
        """, """
            ---@param view WBP_A | WBP_B
            local function bind(view)
                local a = view.Title
                local b = view.BtnClose
                local c = view.Root
            end
        """)
        val errors = highlights.filter { it.description?.contains("不存在") == true }
        assertEquals(1, errors.size)
        assertTrue(errors.first().description!!.contains("Root"))
    }

    /** 非蓝图类（工厂类/普通类）的成员访问不查 */
    fun `test non blueprint class not flagged`() {
        val highlights = highlight(WBP_DEF, """
            local M = ClassViewModel("ShopBuyPanelModel")
            local vm = M.new(nil)
            vm:whatever()
            local x = vm.notExistField
        """)
        assertTrue("不应报不存在，实际：${highlights.map { it.description }}", highlights.none { it.description?.contains("不存在") == true })
    }

    /** 工厂虚拟字段豁免：---@type 覆盖的 ClassView 同名字段不报，真缺失的成员照报 */
    fun `test factory virtual field exempt`() {
        val highlights = highlight(WBP_DEF, """
            ---@type WBP_UI_ShopM12_BuyPanel
            local M = ClassView("shop_buy_panel_view")
            local a = M.shop_buy_panel_view
            local b = M.SomeOtherMissing
        """)
        val errors = highlights.filter { it.description?.contains("不存在") == true }
        assertEquals("只有 SomeOtherMissing 应报，实际：${highlights.map { it.description }}", 1, errors.size)
        assertTrue(errors.first().description!!.contains("SomeOtherMissing"))
    }
}
