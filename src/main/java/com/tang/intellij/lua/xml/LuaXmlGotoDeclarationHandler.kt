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

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlTag
import com.tang.intellij.lua.psi.LuaClassMember
import com.tang.intellij.lua.psi.LuaClassMethod
import com.tang.intellij.lua.psi.LuaIndexExpr
import com.tang.intellij.lua.psi.guessClassType
import com.tang.intellij.lua.psi.resolve
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaShortNameIndex
import java.io.File

/**
 * RPC 跳转扩展：ctrl+click 发送方调用（如 `self.c2dsSender.c2sDestroyMonsterByStaticId(...)`）时，
 * 默认解析落在 [LuaXmlManager] 生成的合成 defs 库（XML 转译产物）里——那只是个中间产物，
 * 对开发无意义。本处理器拦截这种解析结果，改而返回两个真实目标：
 *
 * 1. **XML 定义**：由 defs 类名反推 module（XML 文件名），在工程里定位 `{module}.xml`，
 *    再按 `<rpcName>` 标签定位到具体行（如 iSystemTutorial.xml 的 `<c2sDestroyMonsterByStaticId>`）。
 * 2. **对端实现**：rpc 名 → do-方法名（do + 首字母大写，如 `doC2sDestroyMonsterByStaticId`），
 *    经 [LuaShortNameIndex] 在工程源码里找 `function M:doXxx(...)` 实现
 *    （c2s → server 侧，s2c → client 侧，方向不用区分，全工程搜即可）。
 *
 * 解析结果不在合成 defs 库时返回 null，完全不影响既有跳转（如 net_receiver 里
 * `system:doC2sXxx(...)` 本就直接解析到真实实现）。
 *
 * 平台会把所有 GotoDeclarationHandler 的结果合并（都为空才回退到引用解析），
 * 因此返回的目标会替换掉默认的 defs 跳转；两个目标时 IDE 弹出选择框。
 */
class LuaXmlGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        // 只处理光标点在方法名上的场景（`a.b` / `a:b` 的 b）。
        val id = sourceElement ?: return null
        val indexExpr = (id.parent as? LuaIndexExpr)?.takeIf { it.id == id } ?: return null

        val project = indexExpr.project
        if (DumbService.isDumb(project)) return null

        val context = SearchContext.get(project)
        val resolved = resolve(indexExpr, context) ?: return null

        // 门控：只有解析落在合成 defs 缓存目录里才接管。
        val cacheDir = LuaXmlManager.getInstance(project).getCacheDir()
        val resolvedVf = resolved.containingFile?.virtualFile ?: return null
        if (!isInDir(resolvedVf, cacheDir)) return null

        val member = resolved as? LuaClassMember ?: return null
        val className = member.guessClassType(context)?.className ?: return null
        val module = moduleOfClass(className) ?: return null

        val methodName = indexExpr.name ?: return null
        val rpcName = rpcNameOf(className, methodName)

        val targets = mutableListOf<PsiElement>()
        targets += findXmlTargets(project, module, rpcName)
        targets += findImplTargets(project, doMethodOf(rpcName), cacheDir)
        return if (targets.isEmpty()) null else targets.distinct().toTypedArray()
    }

    /** 在工程里定位 `{module}.xml` 并找到 `<rpcName>` 标签对应的 PSI 元素。 */
    private fun findXmlTargets(project: Project, module: String, rpcName: String): List<PsiElement> {
        val scope = GlobalSearchScope.projectScope(project)
        val files = FilenameIndex.getVirtualFilesByName("$module.xml", scope)
        if (files.isEmpty()) return emptyList()

        // 配了 XML 定义目录时按目录过滤，避免工程里同名 xml 的误命中；
        // 过滤后为空则回退到未过滤结果（防止路径写法差异把全部候选误杀）。
        val defDir = LuaXmlSettings.getInstance(project).xmlDefDir.trim()
            .takeIf { it.isNotEmpty() }?.let { FileUtil.toSystemIndependentName(it) }
        val candidates = if (defDir != null)
            files.filter { FileUtil.isAncestor(defDir, it.path, true) }.ifEmpty { files.toList() }
        else files.toList()

        val psiManager = PsiManager.getInstance(project)
        return candidates.mapNotNull { vf ->
            val psi = psiManager.findFile(vf) ?: return@mapNotNull null
            // 先定到标签起始偏移：优先 XML PSI 按标签名精确查找；失败退化为文本正则。
            // 正则必须基于 psi.text（与 findElementAt 同源）——不能对 VFS 字节算偏移：
            // 文件是 CRLF，打开过的文档/PSI 文本归一化为 LF，混用会按行数累积漂移。
            val tag = findXmlTag(psi, rpcName)
            val offset = tag?.textOffset ?: findRpcTagOffset(psi.text, rpcName) ?: return@mapNotNull null
            // 返回标签名 token（+1 跳过 '<'），而不是 XmlTag 本身：
            // 部分 IDE 版本的跳转弹窗会把 XmlTag 渲染成 <name> 形态（显示成 <>c2sXxx），
            // 叶子 token 的文本就是纯名字，任何版本渲染都正常，导航行号同为标签所在行。
            psi.findElementAt(offset + 1) ?: tag ?: psi
        }
    }

    /** 在 XML PSI 树里递归找名为 [name] 的标签（XmlFile 不可用时返回 null，走文本兜底）。 */
    private fun findXmlTag(element: PsiElement, name: String): XmlTag? {
        if (element is XmlTag && element.name == name) return element
        for (child in element.children) {
            val found = findXmlTag(child, name)
            if (found != null) return found
        }
        return null
    }

    /** 全工程搜 do-方法实现（排除合成 defs 缓存目录里的声明）。 */
    private fun findImplTargets(project: Project, doName: String, cacheDir: File): List<PsiElement> {
        val scope = GlobalSearchScope.projectScope(project)
        return LuaShortNameIndex.instance.get(doName, project, scope)
            .filterIsInstance<LuaClassMethod>()
            .filter {
                val vf = it.containingFile?.virtualFile
                vf != null && !isInDir(vf, cacheDir)
            }
    }

    private fun isInDir(vf: VirtualFile, dir: File): Boolean =
        FileUtil.isAncestor(FileUtil.toSystemIndependentName(dir.path), vf.path, true)

    companion object {
        /**
         * 合成 defs 的类名后缀（见 [RpcXmlTranspiler]），用于从类名反推 module（XML 文件名）。
         * 按长度降序匹配，避免 `ClientReceiverImpl` 被 `Client` 截短。
         */
        private val CLASS_SUFFIXES = listOf(
            "ClientReceiverImpl", "ServerReceiverImpl", "ServerMockImpl",
            "ClientReceiver", "ServerReceiver", "ClientSender", "ServerSender",
            "ClientImpl", "ServerImpl", "ServerMock", "S2SRpc",
            "Client", "Server",
        ).sortedByDescending { it.length }

        /** `iSystemTutorialClientSender` → `iSystemTutorial`；不认识的类名形态返回 null。 */
        internal fun moduleOfClass(className: String): String? =
            CLASS_SUFFIXES.firstOrNull { className.endsWith(it) && className.length > it.length }
                ?.let { className.removeSuffix(it) }

        /**
         * 方法名 → rpc 名：ClientImpl/ServerImpl 里的 do-实现方法剥离 `do` 前缀并首字母小写
         * （`doC2sDestroyMonsterByStaticId` → `c2sDestroyMonsterByStaticId`），其余原样。
         */
        internal fun rpcNameOf(className: String, methodName: String): String {
            val isImpl = className.endsWith("ClientImpl") || className.endsWith("ServerImpl")
            return if (isImpl && methodName.length > 2 &&
                methodName.startsWith("do") && methodName[2].isUpperCase()
            ) methodName.substring(2).replaceFirstChar { it.lowercaseChar() }
            else methodName
        }

        /** rpc 名 → do-实现方法名（`c2sDestroyMonsterByStaticId` → `doC2sDestroyMonsterByStaticId`）。 */
        internal fun doMethodOf(rpcName: String): String =
            "do" + rpcName.replaceFirstChar { it.uppercaseChar() }

        /** `<rpcName>` 起始标签的偏移（排除 `</rpcName>` 闭合标签与同名前缀标签）；找不到返回 null。 */
        internal fun findRpcTagOffset(text: String, rpcName: String): Int? =
            Regex("<${Regex.escape(rpcName)}(?=[\\s>/])").find(text)?.range?.first
    }
}
