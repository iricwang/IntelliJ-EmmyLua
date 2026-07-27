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

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.indexing.FileBasedIndex
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.LuaCallExpr
import com.tang.intellij.lua.psi.LuaLiteralExpr
import com.tang.intellij.lua.psi.LuaLiteralKind
import com.tang.intellij.lua.psi.kind
import com.tang.intellij.lua.psi.stringValue
import com.tang.intellij.lua.stubs.index.LuaStringArgIndex

/**
 * 类工厂函数支持：`local M = ClassActivity("xxx")` / `ClassDialog("xxx")` / `ClassToast("xxx")`。
 *
 * 1. 调用处直接推断出对应的类类型（无需手写 `---@type`）；
 * 2. 通过 [LuaStringArgIndex] 收集工程内所有工厂调用，模拟 fakeApi 中
 *    `---@field X X` 的效果：全局名 `X` 与基类注册表访问（如 `Dialog.X`）
 *    都能解析到带基类的类类型。
 *
 * 生效目录由 [LuaSettings.classFactoryDirs] 配置，为空时不限制。
 */
object LuaClassFactory {

    /** 类工厂函数名 -> 默认基类名 */
    private val BASE_CLASSES = mapOf(
        "ClassActivity" to "Activity",
        "ClassDialog" to "Dialog",
        "ClassToast" to "Toast"
    )

    fun isFactoryFunction(name: String): Boolean = BASE_CLASSES.containsKey(name)

    fun baseClassOf(fnName: String): String? = BASE_CLASSES[fnName]

    /**
     * 由工厂函数名和类名参数推导类名：
     * ClassActivity("arena_endsettle") -> arena_endsettleActivity（追加 Activity 后缀）
     * ClassDialog/ClassToast -> 参数原样
     */
    fun deriveClassName(fnName: String, arg: String): String {
        return if (fnName == "ClassActivity" && !arg.endsWith("Activity")) arg + "Activity" else arg
    }

    /**
     * 从类工厂函数调用中推断类型，如：
     * ```
     * local M = ClassActivity("arena_endsettle") --> arena_endsettleActivity : Activity
     * local M = ClassDialog("arena_detail")      --> arena_detail : Dialog
     * ```
     */
    fun inferCall(callExpr: LuaCallExpr, fnName: String): ITy? {
        val baseClassName = BASE_CLASSES[fnName] ?: return null
        //补全等场景下 PSI 是内存副本，需取 originalFile 拿到真实路径
        val path = callExpr.containingFile?.originalFile?.virtualFile?.path
        if (!LuaSettings.isInClassFactoryDir(path))
            return null
        val arg = callExpr.firstStringArg as? LuaLiteralExpr ?: return null
        if (arg.kind != LuaLiteralKind.String)
            return null
        val argValue = arg.stringValue
        if (argValue.isEmpty())
            return null
        val className = deriveClassName(fnName, argValue)
        return createSerializedClass(className, superClassNames = listOf(baseClassName))
    }

    private val CACHE_KEY = Key<CachedValue<Map<String, String>>>("LuaClassFactory.cache")

    /**
     * 查询全局名/类名是否由类工厂注册，返回其基类名（Activity/Dialog/Toast），未注册返回 null。
     * 索引未就绪或 dumb mode 下返回 null（退化为普通全局名推断）。
     */
    fun getBaseClassName(project: Project, className: String): String? {
        if (DumbService.isDumb(project))
            return null
        var cv = project.getUserData(CACHE_KEY)
        if (cv == null) {
            cv = CachedValuesManager.getManager(project).createCachedValue({
                CachedValueProvider.Result.create(
                    collectRegisteredClasses(project),
                    PsiModificationTracker.getInstance(project)
                )
            })
            project.putUserData(CACHE_KEY, cv)
        }
        return cv.value[className]
    }

    /** 收集工程内所有类工厂调用注册的类：类名 -> 基类名 */
    private fun collectRegisteredClasses(project: Project): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val scope = GlobalSearchScope.projectScope(project)
        BASE_CLASSES.forEach { (fnName, base) ->
            try {
                FileBasedIndex.getInstance().processValues(LuaStringArgIndex.NAME, fnName, null, { file, occ ->
                    if (LuaSettings.isInClassFactoryDir(file.path)) {
                        occ.args.forEach { arg ->
                            if (arg.argIndex == 0 && arg.argString.isNotEmpty())
                                map[deriveClassName(fnName, arg.argString)] = base
                        }
                    }
                    true
                }, scope)
            } catch (e: IndexNotReadyException) {
                //索引重建期间忽略，退化为普通推断
            }
        }
        return map
    }
}
