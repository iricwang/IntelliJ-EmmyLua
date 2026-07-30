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

package com.tang.intellij.lua.ty

import com.tang.intellij.lua.psi.LuaAssignStat
import com.tang.intellij.lua.psi.LuaLiteralExpr
import com.tang.intellij.lua.psi.LuaLiteralKind
import com.tang.intellij.lua.psi.LuaNameExpr
import com.tang.intellij.lua.psi.LuaTableExpr
import com.tang.intellij.lua.psi.LuaVarList
import com.tang.intellij.lua.psi.kind
import com.tang.intellij.lua.psi.stringValue
import com.tang.intellij.lua.psi.valueExpr
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.stubs.index.LuaShortNameIndex

/**
 * 内置标记类型 `KeyClass`：`---@class NameArray : KeyClass` 的子类把
 * 关联全局数组表里的字符串元素当作 key 字段。约定：
 *
 * ```lua
 * ---@class NameArray : KeyClass
 * nameArray = {          -- 关联表：类名同名、或首字母小写的全局数组表
 *     "MSG_HIDE_WAITING",
 *     "MSG_SHOW_WAITING",
 * }
 *
 * ---@type NameArray
 * local nameArray = {}
 * nameArray.MSG_HIDE_WAITING  -- 补全/跳转/类型（字符串字面量）都可用
 * ```
 *
 * `KeyClass` 本身在 std 注解里声明（builtin.lua），使父类解析不报警。
 * 本对象只做两件事：判定 [isKeyClass]，以及按命名约定收集 key（[keysOf]）。
 */
object LuaKeyClass {

    const val KEY_CLASS_NAME = "KeyClass"

    /** 类是否继承自 [KEY_CLASS_NAME]（沿 superClassNames 判断，索引未就绪时返回 false）。 */
    fun isKeyClass(context: SearchContext, className: String): Boolean {
        if (context.isDumb) return false
        val cls = LuaClassIndex.find(className, context) ?: return false
        val type = cls.type
        type.lazyInit(context)
        return KEY_CLASS_NAME in type.superClassNames
    }

    /** 一个 key：名字（即字符串内容）与对应的字符串字面量 PSI（跳转目标）。 */
    class KeyEntry(val name: String, val literal: LuaLiteralExpr)

    /**
     * 收集类关联表里的全部 key。关联表命名约定：与类同名（`NameArray`）
     * 或首字母小写（`nameArray`）的全局数组表，先命中先用。
     */
    fun keysOf(context: SearchContext, className: String): List<KeyEntry> {
        if (context.isDumb) return emptyList()
        val candidates = listOf(
            className.replaceFirstChar { it.lowercaseChar() },
            className
        ).distinct()
        for (candidate in candidates) {
            val keys = collectFromGlobal(context, candidate)
            if (keys.isNotEmpty()) return keys
        }
        return emptyList()
    }

    fun findKey(context: SearchContext, className: String, key: String): KeyEntry? =
        keysOf(context, className).firstOrNull { it.name == key }

    /** 在全局变量 [globalName] 的数组表赋值里收集字符串元素。 */
    private fun collectFromGlobal(context: SearchContext, globalName: String): List<KeyEntry> {
        val result = mutableListOf<KeyEntry>()
        for (element in LuaShortNameIndex.find(globalName, context)) {
            val nameExpr = element as? LuaNameExpr ?: continue
            // 索引返回的是 stub 树元素：数组项（无名 field）不进 stub，
            // 直接在 stub 树上读 tableFieldList 恒为空——按偏移在完整 AST 里重新定位。
            val real = nameExpr.containingFile?.findElementAt(nameExpr.textOffset)
                ?.parent as? LuaNameExpr ?: continue
            // 赋值语句结构：LuaAssignStat(varList=[nameExpr], valueExprList=[table])
            val assignStat = (real.parent as? LuaVarList)?.parent as? LuaAssignStat ?: continue
            val table = assignStat.valueExprList?.exprList?.firstOrNull() as? LuaTableExpr ?: continue
            for (field in table.tableFieldList) {
                val literal = field.valueExpr as? LuaLiteralExpr ?: continue
                if (literal.kind != LuaLiteralKind.String) continue
                val value = literal.stringValue
                if (value.isNotEmpty()) result += KeyEntry(value, literal)
            }
            if (result.isNotEmpty()) break
        }
        return result
    }
}
