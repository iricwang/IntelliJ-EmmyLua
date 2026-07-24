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
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.psi.LuaFileUtil
import javax.swing.Icon

/**
 * 把 [LuaUEBlueprintManager] 的 UE 蓝图注解缓存目录以合成库形式挂入项目。
 * 模式与 [com.tang.intellij.lua.xml.LuaXmlLibraryProvider] 一致。
 */
class LuaUEBlueprintLibraryProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val root = LuaUEBlueprintManager.getInstance(project).getCacheRoot() ?: return emptyList()
        // 注解是嵌套目录结构（如 GamePlay/WBP_X.lua），递归标记为预定义库文件（与 std 一致）。
        VfsUtilCore.iterateChildrenRecursively(root, null, ContentIterator {
            it.putUserData(LuaFileUtil.PREDEFINED_KEY, true)
            true
        })
        return listOf(LuaUEBlueprintLibrary(root))
    }

    class LuaUEBlueprintLibrary(private val root: VirtualFile) : SyntheticLibrary(), ItemPresentation {
        private val roots = listOf(root)

        override fun hashCode() = root.hashCode()

        override fun equals(other: Any?): Boolean = other is LuaUEBlueprintLibrary && other.root == root

        override fun getSourceRoots(): Collection<VirtualFile> = roots

        override fun getLocationString(): String = "UE Blueprint defs"

        override fun getIcon(unused: Boolean): Icon = LuaIcons.FILE

        override fun getPresentableText(): String = "UE Blueprint Defs"
    }
}
