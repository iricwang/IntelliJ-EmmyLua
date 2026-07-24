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

package com.tang.intellij.lua.xml

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.psi.LuaFileUtil
import javax.swing.Icon

/**
 * 把 [LuaXmlManager] 生成的缓存目录以合成库形式挂入项目。
 *
 * 完全仿照 [com.tang.intellij.lua.project.StdLibraryProvider]：标准库怎样把 resources/std 下
 * 一批非项目内的 .lua 定义挂进来走完整 stub index，这里就怎样挂 XML 转译产物的缓存目录。
 */
class LuaXmlLibraryProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val root = LuaXmlManager.getInstance(project).getCacheRoot() ?: return emptyList()

        // 标记为预定义库文件（与 std 一致），使其被当作库源码而非用户可编辑代码处理。
        root.children.forEach {
            it.putUserData(LuaFileUtil.PREDEFINED_KEY, true)
        }
        return listOf(LuaXmlLibrary(root))
    }

    class LuaXmlLibrary(private val root: VirtualFile) : SyntheticLibrary(), ItemPresentation {
        private val roots = listOf(root)

        override fun hashCode() = root.hashCode()

        override fun equals(other: Any?): Boolean = other is LuaXmlLibrary && other.root == root

        override fun getSourceRoots(): Collection<VirtualFile> = roots

        override fun getLocationString(): String = "Lua XML defs"

        override fun getIcon(unused: Boolean): Icon = LuaIcons.FILE

        override fun getPresentableText(): String = "Lua XML Defs"
    }
}
