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

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.util.Processor
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.xml.LuaXmlManager
import com.tang.intellij.test.LuaTestBase
import java.io.File

/**
 * 验证 XML 定义缓存目录经 [com.tang.intellij.lua.xml.LuaXmlLibraryProvider] 以
 * SyntheticLibrary 挂载后，其中的 class 能被索引并被工程内 Lua 文件用于补全。
 */
class TestLuaXmlLibrary : LuaTestBase() {

    private fun writeDefsAndMount(content: String) {
        val manager = LuaXmlManager.getInstance(project)
        val dir = manager.getCacheDir()
        dir.mkdirs()
        File(dir, "defs.lua").writeText(content, Charsets.UTF_8)
        VfsUtil.markDirtyAndRefresh(false, true, true, dir)
        WriteAction.run<RuntimeException> {
            ProjectRootManagerEx.getInstanceEx(project)
                .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)
        }
    }

    fun testClassFromXmlDefsIsIndexed() {
        writeDefsAndMount(
            """
            ---@class HeroServerSender
            local m = {}
            ---@param entityId number @实体ID
            ---@param character_id UINT32
            function m.s2cOnSetCharacterId(entityId, character_id) end

            """.trimIndent()
        )
        val cls = LuaClassIndex.find("HeroServerSender", project, ProjectAndLibrariesScope(project))
        assertNotNull("SyntheticLibrary 中的 class 应能被 LuaClassIndex 检索到", cls)
    }

    /** 场景1（点调用）：receiver 表上的静态方法，`receiver.<caret>` 应补全。 */
    fun testDotCallCompletionFromXmlDefs() {
        writeDefsAndMount(
            """
            ---@class iSystemBattleBagClientReceiver : iSystemBattleBagClientReceiverImpl

            ---@class iSystemBattleBagClientReceiverImpl
            local m = {}
            ---@param system iSystemBattleBagClient @客户端系统
            ---@param gridCount UINT @背包格子数量
            function m.s2cSendAllBagItems(system, gridCount) end

            """.trimIndent()
        )
        myFixture.configureByText(
            "test.lua",
            "---@type iSystemBattleBagClientReceiver\nlocal GamePlay\nGamePlay.<caret>"
        )
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings)
        assertTrue(
            "点调用补全应包含 s2cSendAllBagItems，实际：$strings",
            strings!!.contains("s2cSendAllBagItems")
        )
    }

    /** 场景2（冒号调用）：ClientImpl 的 do-方法（冒号风格），`system:<caret>` 应补全。 */
    fun testColonCallCompletionFromXmlDefs() {
        writeDefsAndMount(
            """
            ---@class iSystemBattleBagClientImpl
            local m = {}
            ---@param gridCount UINT @背包格子数量
            function m:doS2cSendAllBagItems(gridCount) end

            ---@class iSystemBattleBagClient : iSystemBattleBagClientImpl

            """.trimIndent()
        )
        myFixture.configureByText(
            "test.lua",
            "---@type iSystemBattleBagClient\nlocal system\nsystem:<caret>"
        )
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings)
        assertTrue(
            "冒号调用补全应包含 doS2cSendAllBagItems，实际：$strings",
            strings!!.contains("doS2cSendAllBagItems")
        )
    }

    /** 对比诊断：同样的 `local m = {}` 模式分别放在工程文件与 SyntheticLibrary 中，查成员索引。 */
    fun testMemberIndexProjectVsLibrary() {
        val content = { name: String ->
            """
            ---@class $name
            local m = {}
            ---@param entityId number
            function m.s2cOnSetCharacterId(entityId) end

            """.trimIndent()
        }
        writeDefsAndMount(content("LibClass"))
        myFixture.addFileToProject("proj_defs.lua", content("ProjClass"))

        val ctx = SearchContext.get(project)
        var libFound = false
        LuaClassMemberIndex.process("LibClass", "s2cOnSetCharacterId", ctx, Processor {
            libFound = true; false
        })
        var projFound = false
        LuaClassMemberIndex.process("ProjClass", "s2cOnSetCharacterId", ctx, Processor {
            projFound = true; false
        })
        assertTrue("工程文件中的成员应入索引", projFound)
        assertTrue("SyntheticLibrary 中的成员应入索引", libFound)
    }
}
