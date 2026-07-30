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

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.testFramework.IndexingTestUtil
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ue.LuaUEBlueprintSettings
import com.tang.intellij.lua.ue.LuaUnLuaIntelliSenseProvider
import com.tang.intellij.test.LuaTestBase
import java.io.File

/**
 * 验证 [LuaUnLuaIntelliSenseProvider]：UnLua IntelliSense 注解目录
 * （UE 工程下的 Plugins/Core/UnLua/Intermediate/IntelliSense/Script）
 * 以 SyntheticLibrary 挂载后，其中的 UCLASS 注解（如 UWindow）能被索引。
 */
class TestLuaUnLuaIntelliSense : LuaTestBase() {

    private lateinit var ueRoot: File

    /** 在真实磁盘（IDE systemPath，与 LuaXmlManager 缓存目录同机制）造 UE 工程结构。 */
    private fun createIntelliSenseDir() {
        ueRoot = File(PathManager.getSystemPath(), "unlua-is-test-${System.nanoTime()}")
        val scriptDir = File(ueRoot, "Plugins/Core/UnLua/Intermediate/IntelliSense/Script/UnCore")
        assertTrue(scriptDir.mkdirs())
        File(scriptDir, "UWindow.lua").writeText(
            """
            ---@class UWindow
            local m = {}
            function m:GetContentView() end

            """.trimIndent(),
            Charsets.UTF_8
        )
        LuaUEBlueprintSettings.getInstance(project).ueProjectDir = ueRoot.path
        VfsUtil.markDirtyAndRefresh(false, true, true, ueRoot)
    }

    override fun tearDown() {
        try {
            LuaUEBlueprintSettings.getInstance(project).ueProjectDir = ""
            if (::ueRoot.isInitialized) ueRoot.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun rootsChange() {
        WriteAction.run<RuntimeException> {
            ProjectRootManagerEx.getInstanceEx(project)
                .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)
        }
    }

    /** 目录存在时应挂载，UCLASS 注解可经类索引检索到。 */
    fun testIntelliSenseClassIsIndexed() {
        createIntelliSenseDir()
        rootsChange()
        // 轻量测试环境里新挂载库根部的索引调度需要显式等待
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        // provider 应给出包含 Script 目录的合成库
        val libs = LuaUnLuaIntelliSenseProvider().getAdditionalProjectLibraries(project)
        assertEquals(1, libs.size)
        assertTrue(libs.first().sourceRoots.first().path.endsWith("IntelliSense/Script"))

        val cls = LuaClassIndex.find("UWindow", project, ProjectAndLibrariesScope(project))
        assertNotNull("UnLua IntelliSense 中的 UWindow 应能被 LuaClassIndex 检索到", cls)
    }

    /** 目录不存在时不挂载。 */
    fun testNoMountWhenDirMissing() {
        ueRoot = File(PathManager.getSystemPath(), "unlua-is-test-empty-${System.nanoTime()}")
        assertTrue(ueRoot.mkdirs())
        LuaUEBlueprintSettings.getInstance(project).ueProjectDir = ueRoot.path
        assertNull(LuaUnLuaIntelliSenseProvider.resolveScriptDir(project))
        assertTrue(
            LuaUnLuaIntelliSenseProvider().getAdditionalProjectLibraries(project).isEmpty()
        )
    }
}
