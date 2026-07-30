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
import com.intellij.openapi.vfs.VirtualFile
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.psi.LuaFileUtil
import javax.swing.Icon

/**
 * 把 [LuaUEReflectManager] 拉取到的反射注解缓存目录以合成库形式挂入项目。
 * 模式与 [LuaUEBlueprintLibraryProvider] 一致。
 */
class LuaUEReflectLibraryProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val root = LuaUEReflectManager.getInstance(project).getCacheRoot() ?: return emptyList()
        root.children.forEach { it.putUserData(LuaFileUtil.PREDEFINED_KEY, true) }
        return listOf(LuaUEReflectLibrary(root))
    }

    class LuaUEReflectLibrary(private val root: VirtualFile) : SyntheticLibrary(), ItemPresentation {
        private val roots = listOf(root)

        override fun hashCode() = root.hashCode()

        override fun equals(other: Any?): Boolean =
            other is LuaUEReflectLibrary && other.root == root

        override fun getSourceRoots(): Collection<VirtualFile> = roots

        override fun getLocationString(): String = "UE reflect defs"

        override fun getIcon(unused: Boolean): Icon = LuaIcons.FILE

        override fun getPresentableText(): String = "UE Reflect Defs"
    }
}
