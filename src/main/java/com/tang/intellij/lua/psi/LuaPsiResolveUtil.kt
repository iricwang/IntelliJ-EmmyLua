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

package com.tang.intellij.lua.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.LuaClassFactory
import com.tang.intellij.lua.ty.LuaKeyClass

fun resolveLocal(ref: LuaNameExpr, context: SearchContext? = null) = resolveLocal(ref.name, ref, context)

fun resolveLocal(refName:String, ref: PsiElement, context: SearchContext? = null): PsiElement? {
    val element = resolveInFile(refName, ref, context)
    return if (element is LuaNameExpr) null else element
}

fun resolveInFile(refName:String, pin: PsiElement, context: SearchContext?): PsiElement? {
    var ret: PsiElement? = null
    LuaDeclarationTree.get(pin.containingFile).walkUp(pin) { decl ->
        if (decl.name == refName)
            ret = decl.firstDeclaration.psi
        ret == null
    }

    if (ret == null && refName == Constants.WORD_SELF) {
        val methodDef = PsiTreeUtil.getStubOrPsiParentOfType(pin, LuaClassMethodDef::class.java)
        if (methodDef != null && !methodDef.isStatic) {
            val methodName = methodDef.classMethodName
            val expr = methodName.expr
            ret = if (expr is LuaNameExpr && context != null && expr.name != Constants.WORD_SELF)
                resolve(expr, context)
            else
                expr
        }
    }
    return ret
}

fun isUpValue(ref: LuaNameExpr, context: SearchContext): Boolean {
    val funcBody = PsiTreeUtil.getParentOfType(ref, LuaFuncBody::class.java) ?: return false

    val refName = ref.name
    if (refName == Constants.WORD_SELF) {
        val classMethodFuncDef = PsiTreeUtil.getParentOfType(ref, LuaClassMethodDef::class.java)
        if (classMethodFuncDef != null && !classMethodFuncDef.isStatic) {
            val methodFuncBody = classMethodFuncDef.funcBody
            if (methodFuncBody != null)
                return methodFuncBody.textOffset < funcBody.textOffset
        }
    }

    val resolve = resolveLocal(ref, context)
    if (resolve != null) {
        if (!funcBody.textRange.contains(resolve.textRange))
            return true
    }

    return false
}

/**
 * 查找这个引用
 * @param nameExpr 要查找的ref
 * *
 * @param context context
 * *
 * @return PsiElement
 */
fun resolve(nameExpr: LuaNameExpr, context: SearchContext): PsiElement? {
    //search local
    var resolveResult = resolveInFile(nameExpr.name, nameExpr, context)

    //global
    if (resolveResult == null || resolveResult is LuaNameExpr) {
        val target = (resolveResult as? LuaNameExpr) ?: nameExpr
        val refName = target.name
        val moduleName = target.moduleName ?: Constants.WORD_G
        LuaShortNamesManager.getInstance(context.project).processMembers(moduleName, refName, context, {
            resolveResult = it
            false
        })
    }

    return resolveResult
}

fun multiResolve(ref: LuaNameExpr, context: SearchContext): Array<PsiElement> {
    val list = mutableListOf<PsiElement>()
    //search local
    val resolveResult = resolveInFile(ref.name, ref, context)
    if (resolveResult != null) {
        list.add(resolveResult)
    } else {
        val refName = ref.name
        val module = ref.moduleName ?: Constants.WORD_G
        LuaShortNamesManager.getInstance(context.project).processMembers(module, refName, context, {
            list.add(it)
            true
        })
    }
    return list.toTypedArray()
}

fun multiResolve(indexExpr: LuaIndexExpr, context: SearchContext): List<PsiElement> {
    val list = mutableListOf<PsiElement>()
    val name = indexExpr.name ?: return list
    val type = indexExpr.guessParentType(context)
    type.eachTopClass(Processor { ty ->
        val m = ty.findMember(name, context)
        if (m != null)
            list.add(m)
        true
    })
    if (list.isEmpty() && !context.forStub) {
        // 类工厂虚拟字段（Dialog.X 基类注册表 / M.X 自身同名 field / ---@type 覆盖后的同名字段）：
        // 解析到工厂调用点——工厂注册类的"定义处"
        val base = LuaClassFactory.getBaseClassName(context.project, name)
        if (base != null) {
            val declared = LuaClassFactory.getDeclaredTypeName(context.project, name)
            var matched = false
            type.eachTopClass(Processor { ty ->
                if (base == ty.className || name == ty.className || (declared != null && declared == ty.className)) matched = true
                !matched
            })
            if (matched) {
                LuaClassFactory.resolveClassCallSite(context.project, name)?.let { list.add(it) }
            }
        }
    }
    if (list.isEmpty()) {
        // KeyClass 子类：解析到关联全局数组表里的字符串元素
        type.eachTopClass(Processor { ty ->
            LuaKeyClass.findKey(context, ty.className, name)?.let { list.add(it.literal) }
            true
        })
    }
    if (list.isEmpty()) {
        val tree = LuaDeclarationTree.get(indexExpr.containingFile)
        val declaration = tree.find(indexExpr)
        if (declaration != null) {
            list.add(declaration.psi)
        }
    }
    return list
}

fun resolve(indexExpr: LuaIndexExpr, context: SearchContext): PsiElement? {
    val name = indexExpr.name ?: return null
    return resolve(indexExpr, name, context)
}

fun resolve(indexExpr: LuaIndexExpr, idString: String, context: SearchContext): PsiElement? {
    val type = indexExpr.guessParentType(context)
    // ClassViewModel 工厂类：View 属性优先解析到 Defines 的 @param view 标签
    // （优先于基类 ViewModel 的 `---@field View UView`，与 guessFieldType 的类型覆盖一致）
    if (idString == "View" && !context.forStub) {
        var viewParam: PsiElement? = null
        type.eachTopClass(Processor { ty ->
            val def = LuaClassFactory.getViewModelDef(context, ty.className)
            if (def?.viewParam != null) {
                viewParam = def.viewParam
            }
            viewParam == null
        })
        if (viewParam != null) return viewParam
    }
    var ret: PsiElement? = null
    type.eachTopClass(Processor { ty ->
        ret = ty.findMember(idString, context)
        if (ret != null)
            return@Processor false
        true
    })
    if (ret == null && !context.forStub) {
        // 类工厂虚拟字段（Dialog.X 基类注册表 / M.X 自身同名 field / ---@type 覆盖后的同名字段）：
        // 解析到工厂调用点——工厂注册类的"定义处"
        val base = LuaClassFactory.getBaseClassName(context.project, idString)
        if (base != null) {
            val declared = LuaClassFactory.getDeclaredTypeName(context.project, idString)
            type.eachTopClass(Processor { ty ->
                if (base == ty.className || idString == ty.className || (declared != null && declared == ty.className)) {
                    ret = LuaClassFactory.resolveClassCallSite(context.project, idString)
                }
                ret == null
            })
        }
    }
    if (ret == null) {
        // KeyClass 子类：解析到关联全局数组表里的字符串元素（key 字段的定义处）
        type.eachTopClass(Processor { ty ->
            val key = LuaKeyClass.findKey(context, ty.className, idString)
            if (key != null) {
                ret = key.literal
                return@Processor false
            }
            true
        })
    }
    if (ret == null) {
        val tree = LuaDeclarationTree.get(indexExpr.containingFile)
        val declaration = tree.find(indexExpr)
        if (declaration != null) {
            return declaration.psi
        }
    }
    return ret
}

/**
 * 找到 require 的文件路径
 * @param pathString 参数字符串 require "aa.bb.cc"
 * *
 * @param project MyProject
 * *
 * @return PsiFile
 */
fun resolveRequireFile(pathString: String?, project: Project): LuaPsiFile? {
    if (pathString == null)
        return null
    val fileName = pathString.replace('.', '/')
    var f = LuaFileUtil.findFile(project, fileName)

    // issue #415, support init.lua
    if (f == null || f.isDirectory) {
        f = LuaFileUtil.findFile(project, "$fileName/init")
    }

    if (f != null) {
        val psiFile = PsiManager.getInstance(project).findFile(f)
        if (psiFile is LuaPsiFile)
            return psiFile
    }
    return null
}

/**
 * L46 require_ex 的调用方感知解析。
 *
 * 运行时语义（game/global_functions.lua 的 require_ex）：`data.X` 前缀按运行端重写——
 * 客户端读 `client.data.X`，DS 读 `server.data.X`。IDE 静态解析按**调用文件**位置分流：
 * - 路径含 `/client/` → 解析到 `client/data/X.lua`
 * - 路径含 `/server/` 或 `/share/`（share 双侧复用，数据定义以 server 为准）→ `server/data/X.lua`
 * - 其余位置（game/、editor/ 等）不分流，回退普通 require 解析
 *
 * 非 `data.` 前缀的 require_ex 参数与 require 语义一致，也走回退分支。
 *
 * @param contextFile 调用方文件（补全场景注意传 originalFile 的 virtualFile）
 */
fun resolveRequireExFile(pathString: String?, contextFile: VirtualFile?, project: Project): LuaPsiFile? {
    if (pathString != null && pathString.startsWith("data.")) {
        val root = requireExDataRoot(contextFile)
        if (root != null) {
            val fileName = "$root/$pathString".replace('.', '/')
            var f = LuaFileUtil.findFile(project, fileName)
            if (f == null || f.isDirectory) {
                f = LuaFileUtil.findFile(project, "$fileName/init")
            }
            if (f != null && !f.isDirectory) {
                val psiFile = PsiManager.getInstance(project).findFile(f)
                if (psiFile is LuaPsiFile)
                    return psiFile
            }
        }
    }
    return resolveRequireFile(pathString, project)
}

/** require_ex 分流的数据根目录名："client" / "server"；不分流返回 null。 */
fun requireExDataRoot(contextFile: VirtualFile?): String? {
    val path = contextFile?.path ?: return null
    return when {
        path.contains("/client/") -> "client"
        path.contains("/server/") || path.contains("/share/") -> "server"
        else -> null
    }
}