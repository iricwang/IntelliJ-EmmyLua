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

import com.tang.intellij.lua.psi.LuaIndexExpr
import com.tang.intellij.lua.psi.resolve
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.TyStringLiteral
import com.tang.intellij.test.LuaTestBase

/**
 * 验证 KeyClass 内置标记类型：`---@class NameArray : KeyClass` 的子类把
 * 关联全局数组表里的字符串元素当作 key 字段（补全/类型/跳转）。
 */
class TestLuaKeyClass : LuaTestBase() {

    private fun addKeysFile() = myFixture.addFileToProject(
        "keys.lua",
        """
        ---@class NameArray : KeyClass
        nameArray = {
            "MSG_HIDE_WAITING",
            "MSG_SHOW_WAITING",
        }
        """.trimIndent()
    )

    /** 补全：`---@type NameArray` 的变量点后应提示两个 key。 */
    fun testKeyCompletion() {
        addKeysFile()
        myFixture.configureByText(
            "test.lua",
            "---@type NameArray\nlocal nameArray = {}\nnameArray.<caret>"
        )
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings)
        assertTrue("应补全 MSG_HIDE_WAITING，实际：$strings", strings!!.contains("MSG_HIDE_WAITING"))
        assertTrue("应补全 MSG_SHOW_WAITING，实际：$strings", strings.contains("MSG_SHOW_WAITING"))
    }

    /** 类型：key 字段的类型应为字符串字面量（值即名字）。 */
    fun testKeyFieldType() {
        addKeysFile()
        myFixture.configureByText(
            "test.lua",
            "---@type NameArray\nlocal nameArray = {}\nlocal v = nameArray.MSG_HIDE_<caret>WAITING"
        )
        val indexExpr = myFixture.file.findElementAt(myFixture.caretOffset)?.parent as? LuaIndexExpr
        assertNotNull(indexExpr)
        val ty = indexExpr!!.guessType(SearchContext.get(project))
        assertTrue(
            "key 字段应为字符串字面量类型，实际：$ty",
            ty is TyStringLiteral && ty.content == "MSG_HIDE_WAITING"
        )
    }

    /** 跳转：key 字段应解析到关联表里的字符串字面量。 */
    fun testKeyResolve() {
        val keysFile = addKeysFile()
        myFixture.configureByText(
            "test.lua",
            "---@type NameArray\nlocal nameArray = {}\nlocal v = nameArray.MSG_SHOW_<caret>WAITING"
        )
        val indexExpr = myFixture.file.findElementAt(myFixture.caretOffset)?.parent as? LuaIndexExpr
        assertNotNull(indexExpr)
        val resolved = resolve(indexExpr!!, SearchContext.get(project))
        assertNotNull("key 字段应可解析", resolved)
        assertEquals(keysFile.virtualFile, resolved!!.containingFile.virtualFile)
        assertEquals("\"MSG_SHOW_WAITING\"", resolved.text)
    }

    /** 直接用全局表名（文档类绑在全局赋值上）也应解析。 */
    fun testKeyResolveViaGlobalTable() {
        val keysFile = addKeysFile()
        myFixture.configureByText(
            "test.lua",
            "local v = nameArray.MSG_HIDE_<caret>WAITING"
        )
        val indexExpr = myFixture.file.findElementAt(myFixture.caretOffset)?.parent as? LuaIndexExpr
        assertNotNull(indexExpr)
        val resolved = resolve(indexExpr!!, SearchContext.get(project))
        assertNotNull("经全局表的 key 字段应可解析", resolved)
        assertEquals(keysFile.virtualFile, resolved!!.containingFile.virtualFile)
    }
}
