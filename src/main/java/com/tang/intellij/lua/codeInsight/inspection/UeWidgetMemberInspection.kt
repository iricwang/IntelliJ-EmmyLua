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

package com.tang.intellij.lua.codeInsight.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.Processor
import com.tang.intellij.lua.psi.LuaIndexExpr
import com.tang.intellij.lua.psi.LuaVarList
import com.tang.intellij.lua.psi.LuaVisitor
import com.tang.intellij.lua.psi.guessParentType
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.ITyClass
import com.tang.intellij.lua.ty.LuaClassFactory
import com.tang.intellij.lua.ue.LuaUEBlueprintManager

/**
 * 蓝图界面类（WBP）成员存在性检查：表达式的静态类型是蓝图注解类时，
 * 点访问的组件字段 / 冒号调用的函数必须存在于该类（含完整父类链），否则报 ERROR。
 *
 * 「蓝图界面类」判据：类名 WBP_ 前缀（L46 命名约定），或类定义文件在引擎推送的
 * ue-defs 缓存目录（[LuaUEBlueprintManager.isBlueprintDefFile]）。
 *
 * 防误报策略（宁可漏报不可误报）：
 * - 点访问（组件字段）：组件只来自本 WBP 的 widget 树（或其父 WBP 注解），引擎基类
 *   （UView/UUserWidget 等，通常无注解）不贡献 Lua 侧组件字段——**类本身已定义即可判**，
 *   不强制父类链完整（真实环境 UView 普遍无注解，强制链完整会让检查整体失效）；
 * - 冒号调用（函数）：函数大量来自引擎基类，父类链任一环节未定义则无法判定，跳过；
 * - union 类型：成员在任一成分类上存在即视为存在；
 * - 赋值写入（`wbp.X = v`，动态挂字段是合法 Lua）不报，只查读取与调用；
 * - 父类型不是蓝图类（工厂类/普通类/未知类型）不报。
 */
class UeWidgetMemberInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : LuaVisitor() {
            override fun visitIndexExpr(o: LuaIndexExpr) {
                unknownMemberMessage(o)?.let { msg ->
                    holder.registerProblem(o.id ?: o, msg, ProblemHighlightType.ERROR)
                }
                super.visitIndexExpr(o)
            }
        }
    }

    companion object {
        /**
         * 若 [o] 是对蓝图界面类不存在成员的访问，返回错误消息；否则 null。
         * 供 inspection 与 [ViewModelWidgetProblemService]（tab/Wolf 标记）共享。
         */
        fun unknownMemberMessage(o: LuaIndexExpr): String? {
            val fieldName = o.name ?: return null // 仅 wbp.X / wbp:X 形式（排除 wbp["X"]）
            if (o.parent is LuaVarList) return null // 赋值写入 wbp.X = v（动态挂字段）不报

            val context = SearchContext.get(o.project)
            val parentTy = o.guessParentType(context)
            val isColon = o.colon != null

            val judgeableClasses = mutableListOf<ITyClass>()
            var found = false
            parentTy.eachTopClass(Processor { clazz ->
                if (clazz.findMember(fieldName, context) != null) {
                    found = true
                    return@Processor false
                }
                // 类工厂虚拟字段豁免（与 guessFieldType/resolve 同规则）：
                // M.xxxView 自身同名 field、UView.xxxView 基类注册表、---@type 覆盖后的同名字段
                val factoryBase = LuaClassFactory.getBaseClassName(o.project, fieldName)
                if (factoryBase != null) {
                    val declared = LuaClassFactory.getDeclaredTypeName(o.project, fieldName)
                    if (factoryBase == clazz.className || fieldName == clazz.className || declared == clazz.className) {
                        found = true
                        return@Processor false
                    }
                }
                if (isBlueprintClass(o.project, context, clazz) && isJudgeable(clazz, context, isColon)) {
                    judgeableClasses.add(clazz)
                }
                true
            })
            if (found || judgeableClasses.isEmpty()) return null

            val kind = if (isColon) "函数" else "组件"
            return "${kind}不存在：${judgeableClasses.joinToString(" | ") { it.className }} 中没有 \"$fieldName\""
        }

        /**
         * 能否判定成员缺失：点访问（组件）只要求类本身已定义（引擎基类不贡献组件字段）；
         * 冒号调用（函数）要求父类链每一环都有定义（函数多来自引擎基类）。
         */
        private fun isJudgeable(clazz: ITyClass, context: SearchContext, isColon: Boolean): Boolean {
            val manager = LuaShortNamesManager.getInstance(context.project)
            if (manager.findClass(clazz.className, context) == null) return false
            if (!isColon) return true
            return isChainComplete(clazz, context)
        }

        /** 蓝图界面类：WBP_ 前缀命名约定，或定义文件在 ue-defs 缓存目录。 */
        private fun isBlueprintClass(project: Project, context: SearchContext, clazz: ITyClass): Boolean {
            if (clazz.className.startsWith("WBP_")) return true
            var isBlueprint = false
            LuaShortNamesManager.getInstance(project).processClassesWithName(clazz.className, context, Processor { def ->
                val vf = def.containingFile?.virtualFile
                if (vf != null && LuaUEBlueprintManager.getInstance(project).isBlueprintDefFile(vf)) {
                    isBlueprint = true
                }
                !isBlueprint
            })
            return isBlueprint
        }

        /** 父类链上的每个类都已定义，且类本身也已定义（缺一环就无法判定成员是否真不存在）。 */
        private fun isChainComplete(clazz: ITyClass, context: SearchContext): Boolean {
            val manager = LuaShortNamesManager.getInstance(context.project)
            if (manager.findClass(clazz.className, context) == null) return false
            val seen = HashSet<String>()
            seen.add(clazz.className)
            val queue = ArrayDeque<String>()
            queue.addAll(clazz.superClassNames)
            while (queue.isNotEmpty()) {
                val name = queue.removeFirst()
                if (!seen.add(name)) continue
                val def = manager.findClass(name, context) ?: return false
                queue.addAll(def.type.superClassNames)
            }
            return true
        }
    }
}
