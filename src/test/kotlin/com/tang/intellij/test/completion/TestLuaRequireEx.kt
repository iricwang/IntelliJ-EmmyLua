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

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.psi.LuaCallExpr
import com.tang.intellij.lua.psi.LuaIndexExpr
import com.tang.intellij.lua.psi.LuaPsiFile
import com.tang.intellij.lua.reference.LuaRequireReference
import com.tang.intellij.test.LuaTestBase

/**
 * L46 require_ex 的调用方感知解析（[com.tang.intellij.lua.psi.resolveRequireExFile]）：
 * `require_ex("data.X")` 按调用文件路径分流——含 /client/ → client/data/X.lua；
 * 含 /server/ 或 /share/ → server/data/X.lua；其余位置回退普通 require 解析。
 */
class TestLuaRequireEx : LuaTestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "client/data/Message_Def_Data.lua", """
            local M = { client_marker = 1 }
            return M
        """.trimIndent()
        )
        myFixture.addFileToProject(
            "server/data/Message_Def_Data.lua", """
            local M = { server_marker = 1 }
            return M
        """.trimIndent()
        )
    }

    /** 在 callerPath 里写 `require_ex("data.Message_Def_Data")` 并解析引用目标。 */
    private fun resolveDataRequireEx(callerPath: String): PsiElement? {
        val file = myFixture.addFileToProject(
            callerPath, """
            local d = require_ex("data.Message_Def_Data")
            return d
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val call = PsiTreeUtil.findChildOfType(myFixture.file, LuaCallExpr::class.java)!!
        val ref = call.references.filterIsInstance<LuaRequireReference>().firstOrNull()
        assertNotNull("require_ex 调用应挂 LuaRequireReference", ref)
        return ref!!.resolve()
    }

    private fun assertResolveTo(callerPath: String, expectedFileSuffix: String) {
        val target = resolveDataRequireEx(callerPath)
        assertTrue("应解析到 Lua 文件，实际：$target", target is LuaPsiFile)
        val path = (target as LuaPsiFile).virtualFile.path
        assertTrue("应解析到 …/$expectedFileSuffix，实际：$path", path.endsWith(expectedFileSuffix))
    }

    fun `test client caller resolves to client data`() {
        assertResolveTo("client/system/foo_c.lua", "client/data/Message_Def_Data.lua")
    }

    fun `test server caller resolves to server data`() {
        assertResolveTo("server/system/foo_s.lua", "server/data/Message_Def_Data.lua")
    }

    fun `test share caller resolves to server data`() {
        assertResolveTo("share/common/utils/foo.lua", "server/data/Message_Def_Data.lua")
    }

    /** 不在 client/server/share 下的调用方不分流，回退普通 require 解析（含模糊匹配，能找到同名数据文件即可） */
    fun `test other caller falls back to plain require`() {
        val target = resolveDataRequireEx("game/foo.lua")
        assertTrue("回退普通解析应能找到数据文件，实际：$target", target is LuaPsiFile)
        assertEquals("Message_Def_Data.lua", (target as LuaPsiFile).name)
    }

    /** 显式写全前缀（client.data.X）时与 require 行为一致 */
    fun `test explicit prefixed path resolves like require`() {
        val file = myFixture.addFileToProject(
            "share/common/utils/bar.lua", """
            local d = require_ex("client.data.Message_Def_Data")
            return d
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val call = PsiTreeUtil.findChildOfType(myFixture.file, LuaCallExpr::class.java)!!
        val target = call.references.filterIsInstance<LuaRequireReference>().first().resolve()
        assertTrue(target is LuaPsiFile)
        assertTrue((target as LuaPsiFile).virtualFile.path.endsWith("client/data/Message_Def_Data.lua"))
    }

    /** 成员访问应落到分流后文件的字段上（类型推断链路） */
    fun `test member resolve follows caller side data file`() {
        val file = myFixture.addFileToProject(
            "client/system/baz_c.lua", """
            local d = require_ex("data.Message_Def_Data")
            local v = d.client_marker
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val indexExpr = PsiTreeUtil.findChildOfType(myFixture.file, LuaIndexExpr::class.java)!!
        val target = indexExpr.reference?.resolve()
        assertNotNull("d.client_marker 应能解析", target)
        assertTrue(target!!.containingFile.virtualFile.path.endsWith("client/data/Message_Def_Data.lua"))
    }

    /** share 调用方的成员访问应落到 server 变体字段（share 双侧复用，数据定义以 server 为准） */
    fun `test share caller member resolves to server data file`() {
        val file = myFixture.addFileToProject(
            "share/common/utils/qux.lua", """
            local d = require_ex("data.Message_Def_Data")
            local v = d.server_marker
        """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val indexExpr = PsiTreeUtil.findChildOfType(myFixture.file, LuaIndexExpr::class.java)!!
        val target = indexExpr.reference?.resolve()
        assertNotNull("d.server_marker 应能解析", target)
        assertTrue(target!!.containingFile.virtualFile.path.endsWith("server/data/Message_Def_Data.lua"))
    }
}
