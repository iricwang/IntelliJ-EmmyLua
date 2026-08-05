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

package com.tang.intellij.lua.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.editor.completion.LuaDocumentationLookupElement
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ty.*
import com.tang.intellij.lua.ue.LuaUEBlueprintManager
import com.tang.intellij.lua.ue.LuaUEWidgetGotoDeclarationHandler
import com.tang.intellij.lua.ue.LuaUEWidgetOpener

/**
 * Documentation support
 * Created by tangzx on 2016/12/10.
 */
class LuaDocumentationProvider : AbstractDocumentationProvider(), DocumentationProvider {

    companion object {
        /** 「在引擎中打开界面蓝图」文档链接的自定义前缀（psi_element:// 协议之后）。 */
        const val UE_OPEN_WIDGET_LINK_PREFIX = "ue-open-widget:"

        /** 「按资产路径在引擎中打开界面」文档链接的自定义前缀。 */
        const val UE_OPEN_ASSET_LINK_PREFIX = "ue-open-asset:"
    }

    private val renderer: ITyRenderer = object: TyRenderer() {
        override fun renderType(t: String): String {
            return if (t.isNotEmpty()) buildString { DocumentationManagerUtil.createHyperlink(this, t, t, true) } else t
        }
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element != null) {
            when (element) {
                is LuaTypeGuessable -> {
                    val ty = element.guessType(SearchContext.get(element.project))
                    return buildString {
                        renderTy(this, ty, renderer)
                    }
                }
            }
        }
        return super<AbstractDocumentationProvider>.getQuickNavigateInfo(element, originalElement)
    }

    override fun getDocumentationElementForLookupItem(psiManager: PsiManager, obj: Any, element: PsiElement?): PsiElement? {
        if (obj is LuaDocumentationLookupElement) {
            return obj.getDocumentationElement(SearchContext.get(psiManager.project))
        }
        return super<AbstractDocumentationProvider>.getDocumentationElementForLookupItem(psiManager, obj, element)
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement?): PsiElement? {
        // 「在引擎中打开界面蓝图」链接：点击时异步发 HTTP（该方法仅链接点击时调用，不会预取），
        // 返回 null 不做文档内导航。
        if (link.startsWith(UE_OPEN_WIDGET_LINK_PREFIX)) {
            LuaUEWidgetOpener.openWidgetBlueprintByClass(
                psiManager.project, link.removePrefix(UE_OPEN_WIDGET_LINK_PREFIX)
            )
            return null
        }
        if (link.startsWith(UE_OPEN_ASSET_LINK_PREFIX)) {
            LuaUEWidgetOpener.openAsset(psiManager.project, link.removePrefix(UE_OPEN_ASSET_LINK_PREFIX))
            return null
        }
        return LuaClassIndex.find(link, SearchContext.get(psiManager.project))
    }

    /**
     * 界面加载调用（`self:newWidget("BattleGrade/WBP_X", ...)` 等，函数名可配置）的 URL
     * 字符串本身没有 PSI 引用，默认不会触发文档；这里把光标所在的字符串字面量作为文档元素，
     * 使 hover 能展示资产路径与「在引擎中打开」入口。
     */
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        val literal = contextElement?.parent as? LuaLiteralExpr ?: return null
        return if (LuaUEWidgetGotoDeclarationHandler.widgetAssetPathOf(literal) != null) literal else null
    }

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val sb = StringBuilder()
        val tyRenderer = renderer
        when (element) {
            is LuaParamNameDef -> renderParamNameDef(sb, element)
            is LuaLiteralExpr -> renderWidgetUrl(sb, element)
            is LuaDocTagClass -> {
                renderClassDef(sb, element, tyRenderer)
                // 界面蓝图注解类（引擎推送的 ue-defs 缓存文件）：追加「在引擎中打开界面蓝图」入口
                val vf = element.containingFile?.virtualFile
                if (vf != null && element.name != null &&
                    LuaUEBlueprintManager.getInstance(element.project).isBlueprintDefFile(vf)
                ) {
                    sb.append("<a href=\"psi_element://")
                        .append(UE_OPEN_WIDGET_LINK_PREFIX).append(element.name)
                        .append("\">🎮 在引擎中打开界面蓝图</a><br>")
                }
            }
            is LuaClassMember -> renderClassMember(sb, element)
            is LuaNameDef -> { //local xx

                renderDefinition(sb) {
                    sb.append("local <b>${element.name}</b>:")
                    val ty = element.guessType(SearchContext.get(element.project))
                    renderTy(sb, ty, tyRenderer)
                }

                val owner = PsiTreeUtil.getParentOfType(element, LuaCommentOwner::class.java)
                owner?.let { renderComment(sb, owner.comment, tyRenderer) }
            }
            is LuaLocalFuncDef -> {
                sb.wrapTag("pre") {
                    sb.append("local function <b>${element.name}</b>")
                    val type = element.guessType(SearchContext.get(element.project)) as ITyFunction
                    renderSignature(sb, type.mainSignature, tyRenderer)
                }
                renderComment(sb, element.comment, tyRenderer)
            }
        }
        if (sb.isNotEmpty()) return sb.toString()
        return super<AbstractDocumentationProvider>.generateDoc(element, originalElement)
    }

    private fun renderClassMember(sb: StringBuilder, classMember: LuaClassMember) {
        val context = SearchContext.get(classMember.project)
        val parentType = classMember.guessClassType(context)
        val ty = classMember.guessType(context)
        val tyRenderer = renderer

        renderDefinition(sb) {
            //base info
            if (parentType != null) {
                renderTy(sb, parentType, tyRenderer)
                with(sb) {
                    when (ty) {
                        is TyFunction -> {
                            append(if (ty.isColonCall) ":" else ".")
                            append(classMember.name)
                            renderSignature(sb, ty.mainSignature, tyRenderer)
                        }
                        else -> {
                            append(".${classMember.name}:")
                            renderTy(sb, ty, tyRenderer)
                        }
                    }
                }
            } else {
                //NameExpr
                if (classMember is LuaNameExpr) {
                    val nameExpr: LuaNameExpr = classMember
                    with(sb) {
                        append(nameExpr.name)
                        when (ty) {
                            is TyFunction -> renderSignature(sb, ty.mainSignature, tyRenderer)
                            else -> {
                                append(":")
                                renderTy(sb, ty, tyRenderer)
                            }
                        }
                    }

                    val stat = nameExpr.parent.parent // VAR_LIST ASSIGN_STAT
                    if (stat is LuaAssignStat) renderComment(sb, stat.comment, tyRenderer)
                }
            }
        }

        //comment content
        when (classMember) {
            is LuaCommentOwner -> renderComment(sb, classMember.comment, tyRenderer)
            is LuaDocTagField -> renderCommentString("  ", null, sb, classMember.commentString)
            is LuaIndexExpr -> {
                val p1 = classMember.parent
                val p2 = p1.parent
                if (p1 is LuaVarList && p2 is LuaAssignStat) {
                    renderComment(sb, p2.comment, tyRenderer)
                }
            }
        }
    }

    /** 界面 URL 字符串的文档：URL、映射出的资产路径，以及打开引擎资产的链接。 */
    private fun renderWidgetUrl(sb: StringBuilder, literal: LuaLiteralExpr) {
        val assetPath = LuaUEWidgetGotoDeclarationHandler.widgetAssetPathOf(literal) ?: return
        val name = assetPath.substringAfterLast('.')
        renderDefinition(sb) {
            sb.append("界面资产 <b>").append(name).append("</b>")
        }
        sb.append("<b>URL</b>: ").append(literal.stringValue).append("<br>")
        sb.append("<b>资产</b>: ").append(assetPath).append("<br>")
        sb.append("<a href=\"psi_element://")
            .append(UE_OPEN_ASSET_LINK_PREFIX).append(assetPath)
            .append("\">🎮 在引擎中打开界面蓝图</a><br>")
    }

    private fun renderParamNameDef(sb: StringBuilder, paramNameDef: LuaParamNameDef) {
        val owner = PsiTreeUtil.getParentOfType(paramNameDef, LuaCommentOwner::class.java)
        val docParamDef = owner?.comment?.getParamDef(paramNameDef.name)
        val tyRenderer = renderer
        if (docParamDef != null) {
            renderDocParam(sb, docParamDef, tyRenderer, true)
        } else {
            val ty = infer(paramNameDef, SearchContext.get(paramNameDef.project))
            sb.append("<b>param</b> <code>${paramNameDef.name}</code> : ")
            renderTy(sb, ty, tyRenderer)
        }
    }
}
