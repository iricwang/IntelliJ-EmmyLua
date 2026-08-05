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

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.documentation.LuaDocumentationProvider
import com.tang.intellij.lua.ue.LuaUEBlueprintManager
import com.tang.intellij.test.LuaTestBase
import java.io.File

/**
 * 蓝图注解类的 hover 文档应带「在引擎中打开界面蓝图」链接（[LuaDocumentationProvider] 的
 * ue-open-widget 链接）；普通工程内的类不带。
 */
class TestLuaUEWidgetDocLink : LuaTestBase() {

    private val provider = LuaDocumentationProvider()

    override fun setUp() {
        super.setUp()
        LuaUEBlueprintManager.getInstance(project).clearDefs()
    }

    override fun tearDown() {
        try {
            LuaUEBlueprintManager.getInstance(project).clearDefs()
        } finally {
            super.tearDown()
        }
    }

    fun `test blueprint def class doc has open widget link`() {
        val manager = LuaUEBlueprintManager.getInstance(project)
        manager.upsertDef(
            "WBP_UI_Test.lua",
            "---@class WBP_UI_Test\n---@field Title table\nlocal WBP_UI_Test = {}\n"
        )
        val io = File(manager.getCacheDir(), "WBP_UI_Test.lua")
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(io)!!
        val psi = PsiManager.getInstance(project).findFile(vf)!!
        val tagClass = PsiTreeUtil.findChildOfType(psi, LuaDocTagClass::class.java)!!

        val doc = provider.generateDoc(tagClass, null)
        assertNotNull(doc)
        assertTrue(
            "蓝图注解类的文档应含打开蓝图链接，实际：$doc",
            doc!!.contains("ue-open-widget:WBP_UI_Test")
        )
    }

    fun `test project class doc has no open widget link`() {
        val f = myFixture.addFileToProject(
            "defs/local_class.lua",
            "---@class LocalClass\nlocal LocalClass = {}\n"
        )
        val psi = PsiManager.getInstance(project).findFile(f.virtualFile)!!
        val tagClass = PsiTreeUtil.findChildOfType(psi, LuaDocTagClass::class.java)!!

        val doc = provider.generateDoc(tagClass, null)
        assertNotNull(doc)
        assertFalse("普通类不应含打开蓝图链接，实际：$doc", doc!!.contains("ue-open-widget"))
    }

    /** 自定义链接点击：返回 null（不导航）且不抛异常（HTTP 异步发往不可达端口，静默失败） */
    fun `test open widget link resolves to null without side effects`() {
        val target = provider.getDocumentationElementForLink(
            PsiManager.getInstance(project), "ue-open-widget:WBP_UI_Test", null
        )
        assertNull(target)
    }

    /** 界面加载调用的 URL 字符串 hover：文档含资产路径与打开引擎链接。 */
    fun `test widget url literal doc has open asset link`() {
        myFixture.configureByText(
            "test.lua",
            "local self\nself:newWidget(\"BattleGrade/WBP_UI_Treasure<caret>ChestReward_Panel\", View.x)"
        )
        val element = provider.getCustomDocumentationElement(
            myFixture.editor, myFixture.file, myFixture.file.findElementAt(myFixture.caretOffset), myFixture.caretOffset
        )
        assertNotNull("URL 字符串应作为文档元素", element)

        val doc = provider.generateDoc(element!!, null)
        assertNotNull(doc)
        assertTrue(
            "文档应含打开引擎资产链接，实际：$doc",
            doc!!.contains("ue-open-asset:/Game/Res/SGUI/Panel/BattleGrade/WBP_UI_TreasureChestReward_Panel.WBP_UI_TreasureChestReward_Panel")
        )
    }

    /** 非界面加载函数的字符串不接管。 */
    fun `test normal string has no widget doc`() {
        myFixture.configureByText("test.lua", "print(\"Bag/WBP_<caret>X\")")
        assertNull(
            provider.getCustomDocumentationElement(
                myFixture.editor, myFixture.file,
                myFixture.file.findElementAt(myFixture.caretOffset), myFixture.caretOffset
            )
        )
    }

    /** 打开资产链接点击：返回 null（不导航）。 */
    fun `test open asset link resolves to null`() {
        val target = provider.getDocumentationElementForLink(
            PsiManager.getInstance(project), "ue-open-asset:/Game/Res/SGUI/Panel/Bag/WBP_Bag.WBP_Bag", null
        )
        assertNull(target)
    }
}
