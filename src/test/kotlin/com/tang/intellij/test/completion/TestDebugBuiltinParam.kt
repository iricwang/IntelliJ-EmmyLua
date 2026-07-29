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
import com.tang.intellij.lua.comment.psi.LuaDocTagParam
import com.tang.intellij.lua.comment.psi.getType
import com.tang.intellij.test.LuaTestBase

/** 复现：内建类型 table/string/number 在 @param 中的识别 */
class TestDebugBuiltinParam : LuaTestBase() {

    fun `test builtin table param`() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@param info table @房间回收信息（暂未使用）
            ---@param playerData {gbId: string} @玩家进入信息
            local function f(info, playerData) end
            """.trimIndent()
        )
        val params = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaDocTagParam::class.java)
        for (p in params) {
            println("DEBUG PARAM ${p.paramNameRef?.text} => ${getType(p)}")
        }
        assertTrue(params.size == 2)
    }
}
