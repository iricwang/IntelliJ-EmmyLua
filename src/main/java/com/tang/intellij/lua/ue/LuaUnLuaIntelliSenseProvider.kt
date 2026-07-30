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

package com.tang.intellij.lua.ue

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.psi.LuaFileUtil
import java.io.File
import javax.swing.Icon

/**
 * 把 UnLua 的 IntelliSense 注解目录（`{UE工程}/Plugins/Core/UnLua/Intermediate/IntelliSense/Script`）
 * 以合成库形式挂入项目。
 *
 * 该目录由 UnLua 在引擎侧导出（UWindow/UUserWidget 等 UCLASS 的 EmmyLua 注解），
 * 位于 LuaScripts 工程根之外，不挂载的话 `---@return UWindow` 这类类型无法解析，
 * `self:getWindow()` 返回值就没有成员提示。
 *
 * UE 工程目录经 [LuaUEBlueprintManager.resolveUeProjectDir] 解析（设置页可手动覆盖）。
 * 目录不存在（尚未导出）时不挂载；引擎重新导出后内容变化走平台 VFS 文件粒度索引失效。
 */
class LuaUnLuaIntelliSenseProvider : AdditionalLibraryRootsProvider() {

    companion object {
        private const val INTELLISENSE_REL_PATH = "Plugins/Core/UnLua/Intermediate/IntelliSense/Script"

        /** 解析本工程应挂载的 IntelliSense Script 目录；不存在返回 null。 */
        fun resolveScriptDir(project: Project): File? {
            val ueDir = LuaUEBlueprintManager.getInstance(project).resolveUeProjectDir()
            if (ueDir.isEmpty()) return null
            val dir = File(ueDir, INTELLISENSE_REL_PATH)
            return if (dir.isDirectory) dir else null
        }
    }

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val dir = resolveScriptDir(project) ?: return emptyList()
        val root = VfsUtil.findFileByIoFile(dir, true) ?: return emptyList()

        // 标记为预定义库文件（与 std/xml defs 一致，按库源码处理）。
        VfsUtil.processFileRecursivelyWithoutIgnored(root) {
            it.putUserData(LuaFileUtil.PREDEFINED_KEY, true)
            true
        }
        return listOf(LuaUnLuaIntelliSenseLibrary(root))
    }

    class LuaUnLuaIntelliSenseLibrary(private val root: VirtualFile) : SyntheticLibrary(), ItemPresentation {
        private val roots = listOf(root)

        override fun hashCode() = root.hashCode()

        override fun equals(other: Any?): Boolean =
            other is LuaUnLuaIntelliSenseLibrary && other.root == root

        override fun getSourceRoots(): Collection<VirtualFile> = roots

        override fun getLocationString(): String = "UnLua IntelliSense"

        override fun getIcon(unused: Boolean): Icon = LuaIcons.FILE

        override fun getPresentableText(): String = "UnLua IntelliSense"
    }
}
