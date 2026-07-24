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
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.comment.psi.LuaDocTagParam
import com.tang.intellij.lua.comment.psi.getType
import com.tang.intellij.lua.comment.psi.guessType
import com.tang.intellij.lua.psi.LuaParamNameDef
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.TyNil
import com.tang.intellij.lua.ty.TyUnion
import com.tang.intellij.lua.ty.infer
import com.tang.intellij.test.LuaTestBase

/** `name? T` 可空标记（LuaLS 风格）：类型应解析为 T|nil。 */
class TestNullable : LuaTestBase() {

    private fun ITy.containsNil(): Boolean {
        var found = false
        TyUnion.each(this) {
            if (it is TyNil) found = true
        }
        return found
    }

    fun testNullableParam() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@param position? vector3
            local function f(position) end
            """.trimIndent()
        )
        val tagParam = PsiTreeUtil.findChildOfType(myFixture.file, LuaDocTagParam::class.java)
        assertNotNull("@param 标签应能解析（? 不再是 BAD_CHARACTER）", tagParam)
        val ty = getType(tagParam!!)
        assertTrue("position? vector3 应解析为 vector3|nil，实际：$ty", ty.containsNil())
    }

    fun testNonNullableParam() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@param position vector3
            local function f(position) end
            """.trimIndent()
        )
        val tagParam = PsiTreeUtil.findChildOfType(myFixture.file, LuaDocTagParam::class.java)!!
        assertFalse("无 ? 标记不应含 nil", getType(tagParam).containsNil())
    }

    fun testNullableField() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@class Foo
            ---@field pressed? vector2
            local m = {}
            """.trimIndent()
        )
        val tagField = PsiTreeUtil.findChildOfType(myFixture.file, LuaDocTagField::class.java)
        assertNotNull("@field 标签应能解析", tagField)
        val ty = guessType(tagField!!, SearchContext.get(project))
        assertTrue("pressed? vector2 应解析为 vector2|nil，实际：$ty", ty.containsNil())
    }

    /** 引用解析链路：@param ss? 的引用应能解析到函数形参 ss（不报 Cant resolve symbol）。 */
    fun testParamReferenceResolves() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@param ss? table
            local function f(ss) end
            """.trimIndent()
        )
        val ref = PsiTreeUtil.findChildOfType(
            myFixture.file,
            com.tang.intellij.lua.comment.psi.LuaDocParamNameRef::class.java
        )
        assertNotNull(ref)
        assertNotNull("ss? 的引用应解析到形参 ss", ref!!.reference?.resolve())
    }

    /** 成员访问链路：`m.pressed` 经成员索引解析到 @field 定义，类型应含 nil。 */
    fun testFieldMemberAccessNullable() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@class Foo
            ---@field pressed? vector2
            local m = {}
            print(m.pressed)
            """.trimIndent()
        )
        val indexExpr = PsiTreeUtil.findChildOfType(myFixture.file, com.tang.intellij.lua.psi.LuaIndexExpr::class.java)
        assertNotNull(indexExpr)
        val ty = indexExpr!!.guessType(com.tang.intellij.lua.search.SearchContext.get(project))
        assertTrue("m.pressed 类型应含 nil，实际：$ty", ty.containsNil())
    }

    /** hover 实际链路：参数名的类型推断（resolveParamType → stub.docTy）也应含 nil。 */
    fun testParamNameInferNullable() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@param ss? table
            local function f(ss)
                print(ss)
            end
            """.trimIndent()
        )
        val paramNameDef = PsiTreeUtil.findChildOfType(myFixture.file, LuaParamNameDef::class.java)
        assertNotNull(paramNameDef)
        val ty = infer(paramNameDef, SearchContext.get(project))
        assertTrue("参数名推断类型应含 nil（hover 显示 table|nil），实际：$ty", ty.containsNil())
    }

    /** field 尾注释应出现在 hover 文档中。 */
    fun testFieldCommentInHoverDoc() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@class Foo
            ---@field test3Field string 这是注释
            local m = {}
            print(m.test3Field)
            """.trimIndent()
        )
        val indexExpr = PsiTreeUtil.findChildOfType(myFixture.file, com.tang.intellij.lua.psi.LuaIndexExpr::class.java)
        val member = indexExpr!!.reference?.resolve()
        assertNotNull(member)
        val doc = com.tang.intellij.lua.documentation.LuaDocumentationProvider()
            .generateDoc(member!!, indexExpr)
        assertTrue("hover 应显示字段注释，实际：$doc", doc!!.contains("这是注释"))
    }

    /** field 的 `@注释` 形式（XML 生成产物风格）也应出现在 hover 文档中。 */
    fun testFieldAtCommentInHoverDoc() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@class Foo
            ---@field public bulletNum INT @当前弹药数量
            local m = {}
            print(m.bulletNum)
            """.trimIndent()
        )
        val indexExpr = PsiTreeUtil.findChildOfType(myFixture.file, com.tang.intellij.lua.psi.LuaIndexExpr::class.java)
        val member = indexExpr!!.reference?.resolve()
        assertNotNull(member)
        val doc = com.tang.intellij.lua.documentation.LuaDocumentationProvider()
            .generateDoc(member!!, indexExpr)
        assertTrue("hover 应显示字段 @注释，实际：$doc", doc!!.contains("当前弹药数量"))
    }

    fun testNonNullableField() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@class Foo
            ---@field pressed vector2
            local m = {}
            """.trimIndent()
        )
        val tagField = PsiTreeUtil.findChildOfType(myFixture.file, LuaDocTagField::class.java)!!
        assertFalse("无 ? 标记不应含 nil", guessType(tagField, SearchContext.get(project)).containsNil())
    }
}
