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
import com.tang.intellij.lua.search.SearchContext

/**
 * `local M = ClassActivity("xxx")` / `ClassDialog("xxx")` / `ClassToast("xxx")`
 * 类工厂函数的自动类型推断（无需手写 `---@type`）。
 */
class TestClassFactory : TestCompletionBase() {

    /** 调用表达式本身的类型 */
    fun `test call expr type`() {
        val vf = myFixture.addFileToProject(
            "client/game_play/guis/x/a_dialog.lua",
            "local M = ClassDialog(\"AntiCheatDialog\")"
        )
        myFixture.configureFromExistingVirtualFile(vf.virtualFile)
        val call = PsiTreeUtil.findChildOfType(myFixture.file, LuaCallExpr::class.java)!!
        val ty = call.guessType(SearchContext.get(project))
        assertTrue(ty.toString().contains("AntiCheatDialog"))
    }

    /** 成员访问表达式的父类型（补全走的就是这条路径） */
    fun `test member access parent type`() {
        val file = myFixture.addFileToProject(
            "client/game_play/guis/arena_endsettle/arena_endsettle_activity.lua", """
            ---@class Activity
            ---@field theme number
            local Activity = {}

            local M = ClassActivity("arena_endsettle")
            M.foo()
        """.trimIndent()
        )
        val call = PsiTreeUtil.findChildrenOfType(file, LuaCallExpr::class.java).last()
        val indexExpr = call.expr as com.tang.intellij.lua.psi.LuaIndexExpr
        val ty = indexExpr.guessParentType(SearchContext.get(project))
        assertTrue(ty.toString().contains("arena_endsettleActivity"))
    }

    /** ClassActivity("xxx") -> xxxActivity，fakeApi 类不存在时回退到基类 Activity 的成员 */
    fun `test activity infer with base class fallback`() {
        doTest("""
            --- client/game_play/guis/arena_endsettle/arena_endsettle_activity.lua

            ---@class Activity
            ---@field theme number
            local Activity = {}

            local M = ClassActivity("arena_endsettle")
            M.--[[caret]]
        """) {
            assertTrue("theme" in it)
        }
    }

    /** ClassDialog("xxx") -> xxx（类名原样），回退到基类 Dialog 的成员 */
    fun `test dialog infer with verbatim class name`() {
        doTest("""
            --- client/game_lobby/guis/anticheat/anticheat_dialog.lua

            ---@class Dialog
            ---@field flags number
            local Dialog = {}

            local M = ClassDialog("AntiCheatDialog")
            M.--[[caret]]
        """) {
            assertTrue("flags" in it)
        }
    }

    /** fakeApi 已声明 ---@class arena_detail : Dialog 时，推断类型应关联到该类 */
    fun `test dialog infer linked to declared class`() {
        doTest("""
            --- client/game_play/guis/arena_detail/arena_detail_dialog.lua

            ---@class Dialog
            local Dialog = {}

            ---@class arena_detail : Dialog
            ---@field deathInfoModel number

            local M = ClassDialog("arena_detail")
            M.--[[caret]]
        """) {
            assertTrue("deathInfoModel" in it)
        }
    }

    /** function M:OnCreate() 中的 self 也应获得推断类型 */
    fun `test self type in method`() {
        doTest("""
            --- client/game_play/guis/arena_endsettle/arena_endsettle_activity.lua

            ---@class Activity
            ---@field theme number
            local Activity = {}

            local M = ClassActivity("arena_endsettle")
            function M:OnCreate()
                self.--[[caret]]
            end
        """) {
            assertTrue("theme" in it)
        }
    }

    /** 不在配置目录下的文件不做类工厂推断 */
    fun `test not applied outside configured dirs`() {
        doTest("""
            --- client/sgui/core_new/Dialog.lua

            ---@class Dialog
            ---@field flags number
            local Dialog = {}

            local EmptyDialog = ClassDialog("EmptyDialog")
            EmptyDialog.--[[caret]]
        """) {
            assertFalse("flags" in it)
        }
    }

    /** 其他文件直接写全局名 TutorialSelectMaskDialog，应等价于 ---@field X X，带上基类成员 */
    fun `test global name resolves to factory class`() {
        doTest("""
            --- client/game_play/guis/tutorial/mask/tutorial_select_mask_dialog.lua

            ---@class Dialog
            ---@field showInstanceFlag number
            local Dialog = {}

            local M = ClassDialog("TutorialSelectMaskDialog")

            --- client/system/tutorial/tutorial_system_c.lua

            TutorialSelectMaskDialog.--[[caret]]
        """) {
            assertTrue("showInstanceFlag" in it)
        }
    }

    /** 基类注册表访问：Dialog.TutorialSelectMaskDialog 应解析为 TutorialSelectMaskDialog 类 */
    fun `test base class registry access`() {
        doTest("""
            --- client/game_play/guis/tutorial/mask/tutorial_select_mask_dialog.lua

            ---@class Dialog
            ---@field showInstanceFlag number
            local Dialog = {}

            local M = ClassDialog("TutorialSelectMaskDialog")

            --- client/system/tutorial/tutorial_system_c.lua

            Dialog.TutorialSelectMaskDialog.--[[caret]]
        """) {
            assertTrue("showInstanceFlag" in it)
        }
    }

    /** M. 的补全列表应包含与类同名的 field（---@field X X 效果） */
    fun `test self-named field completion`() {
        doTest("""
            --- client/game_play/guis/tutorial/mask/tutorial_select_mask_dialog.lua

            ---@class Dialog
            ---@field flags number
            local Dialog = {}

            local M = ClassDialog("TutorialSelectMaskDialog")
            M.--[[caret]]
        """) {
            assertTrue("TutorialSelectMaskDialog" in it)
            assertTrue("flags" in it)
        }
    }

    /** M.TutorialSelectMaskDialog 的类型应解析回本类 */
    fun `test self-named field type`() {
        doTest("""
            --- client/game_play/guis/tutorial/mask/tutorial_select_mask_dialog.lua

            ---@class Dialog
            ---@field flags number
            local Dialog = {}

            local M = ClassDialog("TutorialSelectMaskDialog")
            M.TutorialSelectMaskDialog.--[[caret]]
        """) {
            assertTrue("flags" in it)
        }
    }

    /** `---@return xxx_dialog` 纯名字引用（无 ---@class 定义）也应继承基类成员 */
    fun `test return type of factory class inherits base members`() {
        doTest("""
            --- client/game_lobby/guis/npctask/lobbynpctask_dialog.lua

            ---@class Dialog
            ---@field flags number
            local Dialog = {}
            function Dialog:getWindow() end

            local M = ClassDialog("lobbynpctask_dialog")

            ---@return lobbynpctask_dialog
            function M.showDialog()
            end

            local w = M.showDialog()
            w.--[[caret]]
        """) {
            assertTrue("getWindow" in it)
            assertTrue("flags" in it)
        }
    }

    /** 工厂类方法返回值类型沿基类链解析：self:getWindow() 应为 UWindow 并可补全其成员 */
    fun `test method return type follows factory base chain`() {
        doTest("""
            --- uwindow.lua

            ---@class UWindow
            local UWindow = {}
            function UWindow:ReceiveInit() end

            --- client/sgui/core/context.lua

            ---@class Context
            local m = {}

            ---@return UWindow
            function m:getWindow()
            end

            --- client/sgui/core/dialog.lua

            ---@class Dialog: Context
            local Dialog = {}

            --- client/game_lobby/guis/npctask/lobbynpctask_dialog.lua

            local M = ClassDialog("lobbynpctask_dialog")

            function M:foo()
                local w = self:getWindow()
                w.--[[caret]]
            end
        """) {
            assertTrue("ReceiveInit" in it)
        }
    }

    /** doc 类型位置（---@return）应能补全出工厂类名（Dialog 原样形态） */
    fun `test factory class name in doc type completion`() {
        val f = myFixture.addFileToProject(
            "client/game_lobby/guis/npctask/caller_dialog.lua",
            "local M = ClassDialog(\"lobbynpctask_dialog\")\n\n---@return lob<caret>\nfunction M.show() end"
        )
        myFixture.configureFromExistingVirtualFile(f.virtualFile)
        myFixture.completeBasic()
        // 唯一候选时补全会自动插入（lookup 为 null 属正常），断言文档结果
        assertTrue(
            "补全后应插入 lobbynpctask_dialog，实际：${myFixture.editor.document.text}",
            myFixture.editor.document.text.contains("---@return lobbynpctask_dialog")
        )
    }

    /** doc 类型位置应能补全出工厂类名（Activity 追加后缀形态） */
    fun `test activity factory class name in doc type completion`() {
        val f = myFixture.addFileToProject(
            "client/game_play/guis/shelter_main/caller_activity.lua",
            "local M = ClassActivity(\"shelter_main\")\n\n---@return shel<caret>\nfunction M.show() end"
        )
        myFixture.configureFromExistingVirtualFile(f.virtualFile)
        myFixture.completeBasic()
        assertTrue(
            "补全后应插入 shelter_mainActivity，实际：${myFixture.editor.document.text}",
            myFixture.editor.document.text.contains("---@return shelter_mainActivity")
        )
    }

    /** `---@return xxxActivity`（Activity 后缀形态）也应继承基类成员 */
    fun `test return type of activity factory class inherits base members`() {
        doTest("""
            --- client/game_play/guis/shelter_main/shelter_main_activity.lua

            ---@class Activity
            local Activity = {}
            function Activity:getOwner() end

            local M = ClassActivity("shelter_main")

            ---@return shelter_mainActivity
            function M.show() end

            local w = M.show()
            w.--[[caret]]
        """) {
            assertTrue("getOwner" in it)
        }
    }

    /** doc 类型名引用应解析到工厂调用点（不再被 UnresolvedClassInspection 标红，且可 Ctrl+B 跳转） */
    fun `test doc type ref resolves to factory call site`() {
        val defFile = myFixture.addFileToProject(
            "client/game_play/guis/shelter_main/shelter_main_activity.lua",
            "local M = ClassActivity(\"shelter_main\")"
        )
        val useFile = myFixture.addFileToProject(
            "client/game_play/guis/shelter_main/caller.lua",
            "---@return shelter_mainActivity\nlocal function show() end"
        )
        val ref = com.intellij.psi.util.PsiTreeUtil.findChildOfType(
            useFile, com.tang.intellij.lua.comment.psi.LuaDocClassNameRef::class.java
        )!!
        val resolved = ref.reference.resolve()
        assertNotNull("工厂类名引用应可解析", resolved)
        assertEquals(defFile.virtualFile, resolved!!.containingFile.virtualFile)
        assertEquals("\"shelter_main\"", resolved.text)

        // UnresolvedClassInspection 不应再报 Unresolved type
        myFixture.enableInspections(com.tang.intellij.lua.codeInsight.inspection.doc.UnresolvedClassInspection())
        myFixture.configureFromExistingVirtualFile(useFile.virtualFile)
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "不应有 Unresolved type 高亮，实际：${highlights.map { it.description }}",
            highlights.none { it.description?.contains("Unresolved type") == true }
        )
    }

    /** ClassViewModel：类型推断 + 回退基类 ViewModel 成员 */
    fun `test viewmodel infer with base class fallback`() {
        doTest("""
            --- client/game_lobby/guis/shelter_main/shelter_main_model.lua

            ---@class ViewModel
            local ViewModel = {}
            function ViewModel:Init() end

            local M = ClassViewModel("ShelterMainPanelModel")
            M.--[[caret]]
        """) {
            assertTrue("Init" in it)
        }
    }

    /** ClassViewModel：Defines 的 @param view 类型暴露为 View 属性（补全 + 成员解析） */
    fun `test viewmodel view property`() {
        val f = myFixture.addFileToProject(
            "client/game_lobby/guis/shelter_main/wbp_model.lua",
            """
            ---@class WBP_UI_Shelter_MainPanel
            local WBP = {}
            WBP.FacilityList = {}

            ---@class WBP_UITaskDetailPanel
            local WBP2 = {}

            local M = ClassViewModel("ShelterMainPanelModel")

            ---@param view WBP_UI_Shelter_MainPanel | WBP_UITaskDetailPanel
            ---@param fields ViewModelFields
            ---@param delegates ViewModelDelegates
            function M:Defines(view, fields, delegates)
                self.View.FacilityList = view.FacilityList
            end

            function M:foo()
                self.View.Facility<caret>
            end
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(f.virtualFile)
        // self.View 应为 union 类型
        val viewIndex = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            f, com.tang.intellij.lua.psi.LuaIndexExpr::class.java
        ).first { it.text == "self.View" }
        val ty = viewIndex.guessType(SearchContext.get(project)).toString()
        assertTrue("self.View 应为 union 类型，实际：$ty",
            ty.contains("WBP_UI_Shelter_MainPanel") && ty.contains("WBP_UITaskDetailPanel"))

        myFixture.completeBasic()
        // 唯一候选 FacilityList 自动插入（lookup 为 null 属正常）
        assertTrue(
            "补全后应插入 FacilityList，实际：${myFixture.editor.document.text}",
            myFixture.editor.document.text.contains("self.View.FacilityList")
        )
    }

    /** ClassViewModel：Defines 的 fields 参数沿 ViewModelFields 解析（Model 子表 = ViewModelFieldGetters） */
    fun `test viewmodel fields param type`() {
        val f = myFixture.addFileToProject(
            "client/game_lobby/guis/shelter_main/wbp_model.lua",
            """
            ---@class ViewModelFieldGetters
            ViewModelFieldGetters = {
                SwitcherModel = function(context, parent) end,
            }

            ---@class ViewModelFields
            ---@field Model ViewModelFieldGetters

            local M = ClassViewModel("ShelterMainPanelModel")

            ---@param view table
            ---@param fields ViewModelFields
            ---@param delegates ViewModelDelegates
            function M:Defines(view, fields, delegates)
                fields.Model.Switcher<caret>
            end
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(f.virtualFile)
        myFixture.completeBasic()
        assertTrue(
            "补全后应插入 SwitcherModel，实际：${myFixture.editor.document.text}",
            myFixture.editor.document.text.contains("fields.Model.SwitcherModel")
        )
    }
}
