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
}
