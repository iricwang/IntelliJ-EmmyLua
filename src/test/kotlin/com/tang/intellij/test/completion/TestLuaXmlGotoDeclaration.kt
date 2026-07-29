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
import com.tang.intellij.lua.xml.LuaXmlGotoDeclarationHandler
import com.tang.intellij.lua.xml.LuaXmlManager
import com.tang.intellij.lua.xml.LuaXmlSettings
import com.tang.intellij.test.LuaTestBase
import java.io.File

/**
 * 验证 [LuaXmlGotoDeclarationHandler]：发送方调用解析落在合成 defs 库时，
 * 跳转目标被替换为「XML 定义行 + 对端 do-实现」。
 */
class TestLuaXmlGotoDeclaration : LuaTestBase() {

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

    private fun gotoTargets(caretOffset: Int = myFixture.caretOffset) =
        LuaXmlGotoDeclarationHandler().getGotoDeclarationTargets(
            myFixture.file.findElementAt(caretOffset), caretOffset, myFixture.editor
        )

    /** c2s 发送方调用 → 应返回 XML 标签与 server 端 do-实现两个目标。 */
    fun testGotoXmlAndServerImplFromClientSenderCall() {
        writeDefsAndMount(
            """
            ---@class iSystemTutorialClientSender
            local m = {}
            ---@param staticId string
            function m.c2sDestroyMonsterByStaticId(staticId) end

            ---@field public c2dsSender iSystemTutorialClientSender
            ---@class iSystemTutorialClient:iSystemTutorialClientImpl

            """.trimIndent()
        )

        // 真实工程里的 XML 是 CRLF 换行：曾按 VFS 原文算偏移、按 PSI 文本找元素，
        // 两者换行符不同导致偏移按行数漂移，落到注释/空白上。这里用 CRLF 内容防回归。
        val xmlFile = myFixture.addFileToProject(
            "share/defs/game_play/rpc/iSystemTutorial.xml",
            "<Root>\r\n" +
                "    <C2SRpcs>\r\n" +
                "        <c2sHealAllHp>\r\n" +
                "        </c2sHealAllHp>\r\n" +
                "        <c2sDestroyMonsterByStaticId>   <!--通过静态ID销毁怪物-->\r\n" +
                "            <Arg Name=\"staticId\">string</Arg>\r\n" +
                "        </c2sDestroyMonsterByStaticId>\r\n" +
                "    </C2SRpcs>\r\n" +
                "</Root>"
        )
        LuaXmlSettings.getInstance(project).xmlDefDir = xmlFile.virtualFile.parent.path

        val implFile = myFixture.addFileToProject(
            "server/game_play/system/tutorial/tutorial_system_s.lua",
            """
            local M = {}

            function M:doC2sDestroyMonsterByStaticId(entityId, staticId)
            end

            return M
            """.trimIndent()
        )

        myFixture.configureByText(
            "test.lua",
            "---@type iSystemTutorialClient\nlocal self\nself.c2dsSender.c2sDestroyMonsterBy<caret>StaticId(staticId)"
        )

        val targets = gotoTargets()
        assertNotNull("应返回跳转目标", targets)
        assertEquals("应恰好有 XML + 实现两个目标，实际：${targets!!.toList()}", 2, targets.size)

        val xmlTarget = targets.firstOrNull { it.containingFile.virtualFile == xmlFile.virtualFile }
        assertNotNull("应包含 XML 目标，实际：${targets.toList()}", xmlTarget)
        // 目标必须是标签名 token（纯名字，弹窗渲染无尖括号），行号即标签所在行（0-based line 4）
        assertEquals("c2sDestroyMonsterByStaticId", xmlTarget!!.text)
        val doc = myFixture.getDocument(xmlFile)
        assertEquals(4, doc.getLineNumber(xmlTarget.textOffset))

        // 弹窗里应带 XML 文件图标（LuaXmlRpcIconProvider）
        assertNotNull(
            "XML 目标应有图标",
            com.tang.intellij.lua.xml.LuaXmlRpcIconProvider().getIcon(xmlTarget, 0)
        )

        val implTarget = targets.firstOrNull { it.containingFile.virtualFile == implFile.virtualFile }
        assertNotNull("应包含 server 实现目标，实际：${targets.toList()}", implTarget)
    }

    /** 实现还没写时（解析落在 defs 的 ServerImpl do-方法上）→ 至少给出 XML 目标。 */
    fun testGotoXmlOnlyWhenImplMissing() {
        writeDefsAndMount(
            """
            ---@class iSystemTutorialClientSender
            local m = {}
            ---@param staticId string
            function m.c2sDestroyMonsterByStaticId(staticId) end

            ---@field public c2dsSender iSystemTutorialClientSender
            ---@class iSystemTutorialClient:iSystemTutorialClientImpl

            """.trimIndent()
        )
        val xmlFile = myFixture.addFileToProject(
            "share/defs/game_play/rpc/iSystemTutorial.xml",
            "<Root>\n    <C2SRpcs>\n        <c2sDestroyMonsterByStaticId>\n        </c2sDestroyMonsterByStaticId>\n    </C2SRpcs>\n</Root>"
        )
        LuaXmlSettings.getInstance(project).xmlDefDir = xmlFile.virtualFile.parent.path

        myFixture.configureByText(
            "test.lua",
            "---@type iSystemTutorialClient\nlocal self\nself.c2dsSender.c2sDestroyMonsterBy<caret>StaticId(staticId)"
        )

        val targets = gotoTargets()
        assertNotNull("实现缺失时仍应返回 XML 目标", targets)
        assertEquals(1, targets!!.size)
        assertEquals(xmlFile.virtualFile, targets[0].containingFile.virtualFile)
    }

    /** 解析不落合成 defs 库（普通工程函数）时不接管，返回 null 走默认跳转。 */
    fun testNoInterceptForPlainProjectMethod() {
        myFixture.addFileToProject(
            "server/foo/foo_s.lua",
            "local M = {}\nfunction M:doSomething(a) end\nreturn M"
        )
        myFixture.configureByText(
            "test.lua",
            "local foo = require('server/foo/foo_s')\nfoo:doSome<caret>thing(1)"
        )
        assertNull(gotoTargets())
    }

    /** 纯函数：类名 → module。 */
    fun testModuleOfClass() {
        assertEquals("iSystemTutorial", LuaXmlGotoDeclarationHandler.moduleOfClass("iSystemTutorialClientSender"))
        assertEquals("iSystemTutorial", LuaXmlGotoDeclarationHandler.moduleOfClass("iSystemTutorialServerReceiverImpl"))
        assertEquals("iSystemTutorial", LuaXmlGotoDeclarationHandler.moduleOfClass("iSystemTutorialServerImpl"))
        assertEquals("Hero", LuaXmlGotoDeclarationHandler.moduleOfClass("HeroS2SRpc"))
        assertNull(LuaXmlGotoDeclarationHandler.moduleOfClass("ClientSender"))
        assertNull(LuaXmlGotoDeclarationHandler.moduleOfClass("SomeRandomClass"))
    }

    /** 纯函数：rpc 名 ↔ do-方法名。 */
    fun testNameMapping() {
        assertEquals(
            "c2sDestroyMonsterByStaticId",
            LuaXmlGotoDeclarationHandler.rpcNameOf("iSystemTutorialServerImpl", "doC2sDestroyMonsterByStaticId")
        )
        assertEquals(
            "c2sDestroyMonsterByStaticId",
            LuaXmlGotoDeclarationHandler.rpcNameOf("iSystemTutorialClientSender", "c2sDestroyMonsterByStaticId")
        )
        assertEquals(
            "doC2sDestroyMonsterByStaticId",
            LuaXmlGotoDeclarationHandler.doMethodOf("c2sDestroyMonsterByStaticId")
        )
    }

    /** 纯函数：XML 标签定位（排除闭合标签与同名前缀）。 */
    fun testFindRpcTagOffset() {
        val text = "<c2sFooBar>\n</c2sFooBar>\n<c2sFoo>\n</c2sFoo>"
        val off = LuaXmlGotoDeclarationHandler.findRpcTagOffset(text, "c2sFoo")
        assertNotNull(off)
        assertEquals(text.indexOf("<c2sFoo>"), off)
        assertNull(LuaXmlGotoDeclarationHandler.findRpcTagOffset(text, "c2sBar"))
    }
}
