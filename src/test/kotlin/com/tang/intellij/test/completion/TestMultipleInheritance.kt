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
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.test.LuaTestBase

/** `---@class Child : Parent1, Parent2` 多重继承。 */
class TestMultipleInheritance : LuaTestBase() {

    private fun configure() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@class Test1
            local t1 = {}
            function t1:methodA() end

            ---@class Test3
            local t3 = {}
            function t3:methodB() end

            ---@class Test2 : Test1, Test3
            local t2 = {}
            """.trimIndent()
        )
    }

    fun testSuperClassListParsed() {
        configure()
        val tagClasses = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaDocTagClass::class.java)
        val test2 = tagClasses.first { it.name == "Test2" }
        assertEquals(
            listOf("Test1", "Test3"),
            test2.superClassNameRefList.map { it.text }
        )
    }

    fun testMemberFromFirstParent() {
        configure()
        var found = false
        LuaClassMemberIndex.process("Test2", "methodA", SearchContext.get(project), {
            found = true; false
        })
        assertTrue("Test2 应能解析到 Test1 的 methodA", found)
    }

    fun testMemberFromSecondParent() {
        configure()
        var found = false
        LuaClassMemberIndex.process("Test2", "methodB", SearchContext.get(project), {
            found = true; false
        })
        assertTrue("Test2 应能解析到 Test3 的 methodB", found)
    }

    fun testMemberCompletionContainsBothParents() {
        configure()
        myFixture.configureByText(
            "use.lua",
            """
            ---@type Test2
            local x
            x:<caret>
            """.trimIndent()
        )
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings)
        assertTrue("补全应含 methodA，实际：$strings", strings!!.contains("methodA"))
        assertTrue("补全应含 methodB，实际：$strings", strings.contains("methodB"))
    }
}
