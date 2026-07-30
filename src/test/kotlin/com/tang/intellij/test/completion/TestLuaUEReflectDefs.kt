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

import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.testFramework.IndexingTestUtil
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ue.LuaUEReflectLibraryProvider
import com.tang.intellij.lua.ue.LuaUEReflectManager
import com.tang.intellij.lua.ue.LuaUEBlueprintSettings
import com.tang.intellij.test.LuaTestBase

/**
 * 验证 [LuaUEReflectManager]：反射注解写盘后
 * 经 [LuaUEReflectLibraryProvider] 挂载可被索引（不依赖真实引擎，直接喂 applyDefs）。
 */
class TestLuaUEReflectDefs : LuaTestBase() {

    private val manager get() = LuaUEReflectManager.getInstance(project)

    override fun tearDown() {
        try {
            manager.getCacheDir().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    /** applyDefs 写盘 → 库挂载 → 类可索引。 */
    fun testReflectDefsAreIndexed() {
        manager.applyDefs(
            mapOf(
                "UWindow" to """
                    ---@class UWindow : UUserWindow
                    local UWindow = {}
                    function UWindow:ReceiveInit() end

                    """.trimIndent()
            )
        )
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        // applyDefs 的 roots change 走 invokeLater，测试里需显式泵事件队列
        com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val libs = LuaUEReflectLibraryProvider().getAdditionalProjectLibraries(project)
        assertEquals(1, libs.size)

        val cls = LuaClassIndex.find("UWindow", project, ProjectAndLibrariesScope(project))
        assertNotNull("反射注解中的 UWindow 应能被 LuaClassIndex 检索到", cls)
    }

    /** 再次 applyDefs 应删除不在结果里的旧文件。 */
    fun testStaleDefsAreRemoved() {
        manager.applyDefs(mapOf("UWindow" to "---@class UWindow\nlocal UWindow = {}\n"))
        assertTrue(manager.getCacheDir().listFiles()!!.isNotEmpty())

        manager.applyDefs(mapOf("UContext" to "---@class UContext\nlocal UContext = {}\n"))
        val names = manager.getCacheDir().listFiles()!!.map { it.name }
        assertTrue("旧文件应被清除，实际：$names", "UWindow.lua" !in names)
        assertTrue("UContext.lua" in names)
    }
}
