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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.util.Processor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.FileBasedIndex
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.LuaCallExpr
import com.tang.intellij.lua.psi.LuaClassMethodDef
import com.tang.intellij.lua.psi.LuaLiteralExpr
import com.tang.intellij.lua.psi.LuaLiteralKind
import com.tang.intellij.lua.psi.LuaLocalDef
import com.tang.intellij.lua.psi.LuaNameExpr
import com.tang.intellij.lua.psi.docTy
import com.tang.intellij.lua.psi.kind
import com.tang.intellij.lua.psi.stringValue
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaStringArgIndex

/**
 * 类工厂函数支持：`local M = ClassActivity("xxx")` / `ClassDialog("xxx")` / `ClassToast("xxx")` /
 * `ClassViewModel("xxx")` / `ClassView("xxx")`。
 *
 * 1. 调用处直接推断出对应的类类型（无需手写 `---@type`）；
 * 2. 通过 [LuaStringArgIndex] 收集工程内所有工厂调用，模拟 fakeApi 中
 *    `---@field X X` 的效果：全局名 `X` 与基类注册表访问（如 `Dialog.X`）
 *    都能解析到带基类的类类型；
 * 3. 声明处带 `---@type T` 时（[getDeclaredTypeName]），同名 field / 基类注册表访问的
 *    字段类型跟随 T（如 `---@type WBP_UI_TreasureChestReward_Panel` + `ClassView(...)`）。
 *
 * 生效目录由 [LuaSettings.classFactoryDirs] 配置，为空时不限制。
 */
object LuaClassFactory {

    /** 类工厂函数名 -> 默认基类名 */
    private val BASE_CLASSES = mapOf(
        "ClassActivity" to "Activity",
        "ClassDialog" to "Dialog",
        "ClassToast" to "Toast",
        "ClassViewModel" to "ViewModel",
        "FastClassViewModel" to "ViewModel",
        "ClassView" to "UView",
    )

    /** ViewModel 工厂类的基类名（[getViewModelDef] 的门控依据）。 */
    const val VIEW_MODEL_BASE = "ViewModel"

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

    /** 一次工厂注册：派生类名 -> 注册信息（基类 + 调用点，供反查跳转）。 */
    data class Registration(
        val baseClass: String,
        val file: VirtualFile,
        val fnName: String,
        val argString: String,
    )

    private val CACHE_KEY = Key<CachedValue<Map<String, Registration>>>("LuaClassFactory.cache.v2")

    private fun cache(project: Project): Map<String, Registration> {
        if (DumbService.isDumb(project))
            return emptyMap()
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
        return cv.value
    }

    /**
     * 查询全局名/类名是否由类工厂注册，返回其基类名（Activity/Dialog/Toast），未注册返回 null。
     * 索引未就绪或 dumb mode 下返回 null（退化为普通全局名推断）。
     */
    fun getBaseClassName(project: Project, className: String): String? =
        cache(project)[className]?.baseClass

    /** 遍历所有类工厂注册的类名（供 doc 类型补全等枚举场景）。 */
    fun processRegisteredClasses(project: Project, processor: Processor<String>): Boolean =
        ContainerUtil.process(cache(project).keys, processor)

    /**
     * 反查注册 [className] 的工厂调用点，返回调用的字符串实参 PSI
     * （工厂类的"定义处"，供 doc 类型名引用解析/跳转/高亮）。
     */
    fun resolveClassCallSite(project: Project, className: String): PsiElement? {
        if (DumbService.isDumb(project)) return null
        val reg = cache(project)[className] ?: return null
        val psi = PsiManager.getInstance(project).findFile(reg.file) ?: return null
        val call = PsiTreeUtil.findChildrenOfType(psi, LuaCallExpr::class.java).firstOrNull { call ->
            (call.expr as? LuaNameExpr)?.name == reg.fnName &&
                (call.firstStringArg as? LuaLiteralExpr)?.stringValue == reg.argString
        } ?: return null
        return call.firstStringArg ?: call
    }

    // ---- @type 声明类型反查（按工厂文件缓存：失效只跟随本文件编辑，杜绝 self. 补全全量重解析） ----

    private val FILE_DECLARED_TYPES_KEY =
        Key<CachedValue<Map<String, String>>>("LuaClassFactory.fileDeclaredTypes.v1")

    /**
     * 提取某个工厂文件里所有工厂调用的 `---@type` 声明类型（注册名 → 声明类型名）。
     * CachedValue 依赖本 PsiFile——编辑其他文件不失效，编辑本文件只重解析本文件。
     */
    private fun declaredTypesInFile(project: Project, file: VirtualFile): Map<String, String> {
        val psi = PsiManager.getInstance(project).findFile(file) ?: return emptyMap()
        return CachedValuesManager.getCachedValue(psi, FILE_DECLARED_TYPES_KEY) {
            val map = mutableMapOf<String, String>()
            PsiTreeUtil.findChildrenOfType(psi, LuaCallExpr::class.java).forEach { call ->
                val fnName = (call.expr as? LuaNameExpr)?.name ?: return@forEach
                if (!BASE_CLASSES.containsKey(fnName)) return@forEach
                val arg = (call.firstStringArg as? LuaLiteralExpr)?.stringValue ?: return@forEach
                val localDef = PsiTreeUtil.getParentOfType(call, LuaLocalDef::class.java)
                val declared =
                    (localDef?.nameList?.nameDefList?.firstOrNull()?.docTy as? ITyClass)?.className
                if (declared != null) {
                    map[deriveClassName(fnName, arg)] = declared
                }
            }
            CachedValueProvider.Result.create(map, psi)
        }
    }

    /**
     * 工厂调用处 local 声明上的 `---@type` 覆盖类型名（如
     * `---@type WBP_UI_TreasureChestReward_Panel` + `local M = ClassView("arena_chest_reward_view")`
     * → "WBP_UI_TreasureChestReward_Panel"）；无标注/未注册/非单一类类型返回 null。
     */
    fun getDeclaredTypeName(project: Project, className: String): String? {
        if (DumbService.isDumb(project)) return null
        val reg = cache(project)[className] ?: return null
        return declaredTypesInFile(project, reg.file)[className]
    }

    /**
     * 按 `---@type` 声明类型反查工厂注册名（如 "WBP_UI_TreasureChestReward_Panel" →
     * ["arena_chest_reward_view"]）。逐注册项命中按文件缓存的声明类型——单文件编辑只重解析
     * 该文件；首个文件解析一次后常驻缓存，不会在补全线程反复全量解析。
     */
    fun getRegisteredByDeclaredType(project: Project, declaredTypeName: String): List<String> {
        if (DumbService.isDumb(project)) return emptyList()
        val result = mutableListOf<String>()
        cache(project).forEach { (registered, reg) ->
            if (declaredTypesInFile(project, reg.file)[registered] == declaredTypeName) {
                result.add(registered)
            }
        }
        return result
    }

    /** ViewModel 类 Defines 方法提取的信息：[viewType] 为 `@param view` 的类型（可 union），[viewParam] 为该 @param 标签（"View" 属性的跳转目标）。 */
    class ViewModelDef(val viewType: ITy?, val viewParam: PsiElement?)

    /**
     * 解析 ViewModel 工厂类（ClassViewModel 注册）的 Defines 方法：
     * `function M:Defines(view, fields, delegates)` 的 `---@param view` 类型
     * 暴露为该类的 `View` 属性（如 `self.View.FacilityList`）。
     * 非 ViewModel 工厂类或未注册类返回 null。
     */
    fun getViewModelDef(context: SearchContext, className: String): ViewModelDef? {
        if (getBaseClassName(context.project, className) != VIEW_MODEL_BASE) return null
        val reg = cache(context.project)[className] ?: return null
        val psi = PsiManager.getInstance(context.project).findFile(reg.file) ?: return null
        val defines = PsiTreeUtil.findChildrenOfType(psi, LuaClassMethodDef::class.java)
            .firstOrNull { it.name == "Defines" } ?: return ViewModelDef(null, null)
        val viewTag = defines.comment?.getParamDef("view")
        return ViewModelDef(viewTag?.type, viewTag ?: defines.nameIdentifier)
    }

    /** 收集工程内所有类工厂调用注册的类：类名 -> 注册信息 */
    private fun collectRegisteredClasses(project: Project): Map<String, Registration> {
        val map = mutableMapOf<String, Registration>()
        val scope = GlobalSearchScope.projectScope(project)
        BASE_CLASSES.forEach { (fnName, base) ->
            try {
                FileBasedIndex.getInstance().processValues(LuaStringArgIndex.NAME, fnName, null, { file, occ ->
                    if (LuaSettings.isInClassFactoryDir(file.path)) {
                        occ.args.forEach { arg ->
                            if (arg.argIndex == 0 && arg.argString.isNotEmpty())
                                map[deriveClassName(fnName, arg.argString)] =
                                    Registration(base, file, fnName, arg.argString)
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
