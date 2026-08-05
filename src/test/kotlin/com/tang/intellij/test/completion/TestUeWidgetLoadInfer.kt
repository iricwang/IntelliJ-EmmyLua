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

import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.psi.LuaCallExpr
import com.tang.intellij.lua.psi.LuaIndexExpr
import com.tang.intellij.lua.psi.LuaParamNameDef
import com.tang.intellij.lua.psi.guessType
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.test.LuaTestBase

/**
 * 界面加载函数（widgetGotoFunctions）的类型推断：
 * - 同步函数（newWidget/createViewByUrl）：返回值 = URL 末段的界面蓝图类
 * - 异步函数（newWidgetAsync/createWidgetAsync）：回调首参 = URL 末段的界面蓝图类
 */
class TestUeWidgetLoadInfer : LuaTestBase() {

    private fun callTypeOf(code: String): String {
        val file = myFixture.addFileToProject("client/game_lobby/guis/x/caller.lua", code.trimIndent())
        val call = PsiTreeUtil.findChildrenOfType(file, LuaCallExpr::class.java).first()
        return call.guessType(SearchContext.get(project)).toString()
    }

    fun `test newWidget return type`() {
        val ty = callTypeOf("""
            local function f(self)
                return self:newWidget("BattleGrade/WBP_UI_TreasureChestReward_Panel")
            end
        """)
        assertTrue("返回值应为蓝图类，实际：$ty", ty.contains("WBP_UI_TreasureChestReward_Panel"))
    }

    fun `test createViewByUrl return type`() {
        val ty = callTypeOf("""
            local function f(ctx)
                return ctx:createViewByUrl("Common/WBP_UI_CommonTag_Square")
            end
        """)
        assertTrue("返回值应为蓝图类，实际：$ty", ty.contains("WBP_UI_CommonTag_Square"))
    }

    /** 异步函数返回值不是 widget，不应给蓝图类型（避免成员检查误报） */
    fun `test async return not typed`() {
        val ty = callTypeOf("""
            local function f(self)
                return self:newWidgetAsync("BattleGrade/WBP_UI_TreasureChestReward_Panel", function(w) end)
            end
        """)
        assertFalse("异步返回值不应是蓝图类，实际：$ty", ty.contains("WBP_UI_TreasureChestReward_Panel"))
    }

    fun `test createWidgetAsync callback param type`() {
        val file = myFixture.addFileToProject("client/game_lobby/guis/x/caller.lua", """
            local function f(context)
                context:createWidgetAsync("Insidemain/WBP_UI_Skill_Component_CD", function(widget)
                    local x = widget
                end)
            end
        """.trimIndent())
        val param = PsiTreeUtil.findChildrenOfType(file, LuaParamNameDef::class.java)
            .first { it.name == "widget" }
        val ty = param.guessType(SearchContext.get(project)).toString()
        assertTrue("回调参数应为蓝图类，实际：$ty", ty.contains("WBP_UI_Skill_Component_CD"))
    }

    /** 回调参数类型端到端：widget 的成员应解析到蓝图类字段 */
    fun `test callback param member resolves to widget class field`() {
        myFixture.addFileToProject("client/game_lobby/guis/x/wbp_defs.lua", """
            ---@class WBP_UI_Skill_Component_CD
            ---@field CdText table
            local WBP_UI_Skill_Component_CD = {}
        """.trimIndent())
        val file = myFixture.addFileToProject("client/game_lobby/guis/x/caller.lua", """
            local function f(context)
                context:createWidgetAsync("Insidemain/WBP_UI_Skill_Component_CD", function(widget)
                    widget.CdText = nil
                end)
            end
        """.trimIndent())
        val indexExpr = PsiTreeUtil.findChildrenOfType(file, LuaIndexExpr::class.java)
            .first { it.name == "CdText" }
        val resolved = indexExpr.reference?.resolve()
        assertNotNull("widget.CdText 应解析到蓝图类字段", resolved)
        assertEquals("CdText", (resolved as? com.tang.intellij.lua.psi.LuaClassMember)?.name)
    }

    /** 非配置函数不受影响 */
    fun `test non widget function unaffected`() {
        val ty = callTypeOf("""
            local function f(self)
                return self:someOtherLoader("BattleGrade/WBP_UI_TreasureChestReward_Panel")
            end
        """)
        assertFalse("未配置函数不应推断蓝图类型，实际：$ty", ty.contains("WBP_UI_TreasureChestReward_Panel"))
    }
}
