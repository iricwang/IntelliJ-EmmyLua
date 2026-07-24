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

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.Processor
import com.intellij.util.io.StringRef
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocTableDef
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.search.LuaClassInheritorsSearch
import com.tang.intellij.lua.psi.search.LuaShortNamesManager
import com.tang.intellij.lua.search.SearchContext

interface ITyClass : ITy {
    val className: String
    val varName: String
    /** All direct parent class names (multiple inheritance). */
    val superClassNames: List<String>
    /** First parent class name, kept for backwards compatibility. */
    var superClassName: String?
    /** Interfaces this class explicitly implements via @implements. */
    val implementsClassNames: List<String>
    var aliasName: String?
    fun processAlias(processor: Processor<String>): Boolean
    fun lazyInit(searchContext: SearchContext)
    fun getMemberChain(context: SearchContext): ClassMemberChain
    fun processMembers(context: SearchContext, processor: (ITyClass, LuaClassMember) -> Unit, deep: Boolean = true)
    fun processMembers(context: SearchContext, processor: (ITyClass, LuaClassMember) -> Unit) {
        processMembers(context, processor, true)
    }
    fun findMember(name: String, searchContext: SearchContext): LuaClassMember?
    fun findMemberType(name: String, searchContext: SearchContext): ITy? {
        return infer(findMember(name, searchContext), searchContext)
    }
    fun findSuperMember(name: String, searchContext: SearchContext): LuaClassMember?

    fun recoverAlias(context: SearchContext, aliasSubstitutor: TyAliasSubstitutor): ITy {
        return this
    }
}

fun ITyClass.isVisibleInScope(project: Project, contextTy: ITy, visibility: Visibility): Boolean {
    if (visibility == Visibility.PUBLIC)
        return true
    var isVisible = false
    TyUnion.process(contextTy) {
        if (it is ITyClass) {
            if (it == this)
                isVisible = true
            else if (visibility == Visibility.PROTECTED) {
                isVisible = LuaClassInheritorsSearch.isClassInheritFrom(GlobalSearchScope.projectScope(project), project, className, it.className)
            }
        }
        !isVisible
    }
    return isVisible
}

abstract class TyClass(override val className: String,
                       override val varName: String = "",
                       superClassNames: List<String> = emptyList(),
                       implementsClassNames: List<String> = emptyList()
) : Ty(TyKind.Class), ITyClass {

    // Internal mutable storage for all parent class names.
    private val _superClassNames: MutableList<String> = superClassNames.toMutableList()
    private val _implementsClassNames: MutableList<String> = implementsClassNames.toMutableList()

    override val superClassNames: List<String> get() = _superClassNames
    override val implementsClassNames: List<String> get() = _implementsClassNames

    /** First parent class name; setting replaces the entire parent list with a single entry. */
    override var superClassName: String?
        get() = _superClassNames.firstOrNull()
        set(value) {
            _superClassNames.clear()
            if (value != null) _superClassNames.add(value)
        }

    final override var aliasName: String? = null

    private var _lazyInitialized: Boolean = false

    override fun equals(other: Any?): Boolean {
        return other is ITyClass && other.className == className && other.flags == flags
    }

    override fun hashCode(): Int {
        return className.hashCode()
    }

    override fun processAlias(processor: Processor<String>): Boolean {
        val alias = aliasName
        if (alias == null || alias == className)
            return true
        if (!processor.process(alias))
            return false
        if (!isGlobal && !isAnonymous && LuaSettings.instance.isRecognizeGlobalNameAsType)
            return processor.process(getGlobalTypeName(className))
        return true
    }

    override fun getMemberChain(context: SearchContext): ClassMemberChain {
        lazyInit(context)
        val manager = LuaShortNamesManager.getInstance(context.project)
        // Build chains for all parents (superclass + implements interfaces)
        val allParentNames = (superClassNames + implementsClassNames).distinct()
        val superChains = allParentNames.mapNotNull { clsName ->
            if (clsName == className) null
            else (Ty.getBuiltin(clsName) ?: manager.findClass(clsName, context)?.type) as? ITyClass
        }.map { it.getMemberChain(context) }

        val chain = ClassMemberChain(this, superChains)
        val members = manager.getClassMembers(className, context)
        members.forEach { chain.add(it) }

        processAlias(Processor { alias ->
            val classMembers = manager.getClassMembers(alias, context)
            classMembers.forEach { chain.add(it) }
            true
        })

        return chain
    }

    override fun processMembers(context: SearchContext, processor: (ITyClass, LuaClassMember) -> Unit, deep: Boolean) {
        val chain = getMemberChain(context)
        chain.process(deep, processor)
    }

    override fun findMember(name: String, searchContext: SearchContext): LuaClassMember? {
        val chain = getMemberChain(searchContext)
        return chain.findMember(name)
    }

    override fun findSuperMember(name: String, searchContext: SearchContext): LuaClassMember? {
        val chain = getMemberChain(searchContext)
        return chain.superChain?.findMember(name)
    }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitClass(this)
    }

    override fun lazyInit(searchContext: SearchContext) {
        if (!_lazyInitialized) {
            _lazyInitialized = true
            doLazyInit(searchContext)
        }
    }

    open fun doLazyInit(searchContext: SearchContext) {
        val classDef = LuaShortNamesManager.getInstance(searchContext.project).findClass(className, searchContext)
        if (classDef != null && aliasName == null) {
            val tyClass = classDef.type
            aliasName = tyClass.aliasName
            _superClassNames.clear()
            _superClassNames.addAll(tyClass.superClassNames)
            _implementsClassNames.clear()
            _implementsClassNames.addAll(tyClass.implementsClassNames)
        }
    }

    override fun getSuperClass(context: SearchContext): ITy? {
        lazyInit(context)
        val clsName = superClassNames.firstOrNull()
        if (clsName != null && clsName != className) {
            return Ty.getBuiltin(clsName) ?: LuaShortNamesManager.getInstance(context.project).findClass(clsName, context)?.type
        }
        return null
    }

    override fun subTypeOf(other: ITy, context: SearchContext, strict: Boolean): Boolean {
        // class extends table
        if (other == Ty.TABLE) return true
        if (super.subTypeOf(other, context, strict)) return true

        this.doLazyInit(context)
        // Check if any of the superclasses or implemented interfaces match
        var isSubType = false
        processSuperClass(this, context) { superType ->
            isSubType = superType == other
            !isSubType
        }
        if (!isSubType) {
            processImplements(this, context) { iface ->
                isSubType = iface == other
                !isSubType
            }
        }
        return isSubType
    }

    override fun substitute(substitutor: ITySubstitutor): ITy {
        return substitutor.substitute(this)
    }

    companion object {
        /*
         * WARNING: Risk of classloading deadlock
         *
         * Calling `createSerializedClass` uses the type `TyClass`.
         * However, using the type `TyClass` requires all its static fields
         * to be initialized. So the JVM can't run `createSerializedClass` without
         * having `TyClass`, and it can't use `TyClass` without running `createSerializedClass`.
         * Thus the JVM deadlocks during classloading, resulting in frozen indexing...
         * 
         * Workaround this by using Kotlin lazy properties,
         * so `createSerializedClass` is not run until TyClass.G is actually accessed.
         *
         * See issue #510 and Ty.kt for more info on this bug.
         * -- Techcable
         */

        // for _G
        val G: TyClass by lazy { createSerializedClass(Constants.WORD_G) }

        fun createAnonymousType(nameDef: LuaNameDef): TyClass {
            val stub = nameDef.stub
            val tyName = stub?.anonymousType ?: getAnonymousType(nameDef)
            return createSerializedClass(tyName, nameDef.name, null, null, TyFlags.ANONYMOUS)
        }

        fun createGlobalType(nameExpr: LuaNameExpr, store: Boolean): ITy {
            val name = nameExpr.name
            val g = createSerializedClass(getGlobalTypeName(nameExpr), name, null, null, TyFlags.GLOBAL)
            if (!store && LuaSettings.instance.isRecognizeGlobalNameAsType)
                return createSerializedClass(name, name, null, null, TyFlags.GLOBAL).union(g)
            return g
        }

        fun createGlobalType(name: String): ITy {
            val g = createSerializedClass(getGlobalTypeName(name), name, null, null, TyFlags.GLOBAL)
            if (LuaSettings.instance.isRecognizeGlobalNameAsType)
                return createSerializedClass(name, name, null, null, TyFlags.GLOBAL).union(g)
            return g
        }

        fun processSuperClass(start: ITyClass, searchContext: SearchContext, processor: (ITyClass) -> Boolean): Boolean {
            val processedNames = mutableSetOf<String>()
            processedNames.add(start.className)
            val queue = ArrayDeque<String>()
            start.lazyInit(searchContext)
            queue.addAll(start.superClassNames)

            val manager = LuaShortNamesManager.getInstance(searchContext.project)
            while (queue.isNotEmpty()) {
                val clsName = queue.removeFirst()
                if (!processedNames.add(clsName)) continue  // cycle guard

                val cls = (Ty.getBuiltin(clsName) ?: manager.findClass(clsName, searchContext)?.type) as? ITyClass
                    ?: continue

                if (!processor(cls)) return false
                cls.lazyInit(searchContext)
                queue.addAll(cls.superClassNames)
            }
            return true
        }

        /** Traverses the direct implements list (BFS, one level). */
        fun processImplements(start: ITyClass, searchContext: SearchContext, processor: (ITyClass) -> Boolean): Boolean {
            start.lazyInit(searchContext)
            val manager = LuaShortNamesManager.getInstance(searchContext.project)
            for (ifaceName in start.implementsClassNames) {
                val iface = (Ty.getBuiltin(ifaceName) ?: manager.findClass(ifaceName, searchContext)?.type) as? ITyClass
                    ?: continue
                if (!processor(iface)) return false
                // Also traverse the interface's own inheritance
                if (!processSuperClass(iface, searchContext, processor)) return false
            }
            return true
        }
    }
}

class TyPsiDocClass(tagClass: LuaDocTagClass) : TyClass(
    tagClass.name!!,
    superClassNames = tagClass.superClassNameRefList.map { it.text },
    implementsClassNames = tagClass.implementsNamesFromComment()
) {

    init {
        aliasName = tagClass.aliasName
    }

    override fun doLazyInit(searchContext: SearchContext) {}
}

open class TySerializedClass(name: String,
                             varName: String = name,
                             superClassNames: List<String> = emptyList(),
                             alias: String? = null,
                             flags: Int = 0,
                             implementsClassNames: List<String> = emptyList())
    : TyClass(name, varName, superClassNames, implementsClassNames) {
    init {
        aliasName = alias
        this.flags = flags
    }

    override fun recoverAlias(context: SearchContext, aliasSubstitutor: TyAliasSubstitutor): ITy {
        if (this.isAnonymous || this.isGlobal)
            return this
        val alias = LuaShortNamesManager.getInstance(context.project).findAlias(className, context)
        return alias?.type?.substitute(aliasSubstitutor) ?: this
    }
}

//todo Lazy class ty
class TyLazyClass(name: String) : TySerializedClass(name)

fun createSerializedClass(name: String,
                          varName: String = name,
                          superClassNames: List<String> = emptyList(),
                          alias: String? = null,
                          flags: Int = 0,
                          implementsClassNames: List<String> = emptyList()): TyClass {
    val list = name.split("|")
    if (list.size == 3) {
        val type = list[0].toInt()
        if (type == 10) {
            return TySerializedDocTable(name)
        }
    }

    return TySerializedClass(name, varName, superClassNames, alias, flags, implementsClassNames)
}

/** Backward-compatible overload that accepts a single nullable super class name. */
fun createSerializedClass(name: String,
                          varName: String = name,
                          supper: String? = null,
                          alias: String? = null,
                          flags: Int = 0): TyClass {
    return createSerializedClass(name, varName, listOfNotNull(supper), alias, flags)
}

private val PsiFile.uid: String get() {
    if (this is LuaPsiFile)
        return this.uid

    return name
}

fun getTableTypeName(table: LuaTableExpr): String {
    val stub = table.stub
    if (stub != null)
        return stub.tableTypeName

    val fileName = table.containingFile.uid
    return "$fileName@(${table.node.startOffset})table"
}

fun getAnonymousType(nameDef: LuaNameDef): String {
    return "${nameDef.node.startOffset}@${nameDef.containingFile.uid}"
}

fun getGlobalTypeName(text: String): String {
    return if (text == Constants.WORD_G) text else "$$text"
}

fun getGlobalTypeName(nameExpr: LuaNameExpr): String {
    return getGlobalTypeName(nameExpr.name)
}

class TyTable(val table: LuaTableExpr) : TyClass(getTableTypeName(table)) {
    init {
        this.flags = TyFlags.ANONYMOUS or TyFlags.ANONYMOUS_TABLE
    }

    override fun processMembers(context: SearchContext, processor: (ITyClass, LuaClassMember) -> Unit, deep: Boolean) {
        for (field in table.tableFieldList) {
            processor(this, field)
        }
        super.processMembers(context, processor, deep)
    }

    override fun toString(): String = displayName

    override fun doLazyInit(searchContext: SearchContext) = Unit

    override fun subTypeOf(other: ITy, context: SearchContext, strict: Boolean): Boolean {
        // Empty list is a table, but subtype of all lists
        return super.subTypeOf(other, context, strict) || other == Ty.TABLE || (other is TyArray && table.tableFieldList.size == 0)
    }
}

fun getDocTableTypeName(table: LuaDocTableDef): String {
    val stub = table.stub
    if (stub != null)
        return stub.className

    val fileName = table.containingFile.uid
    return "10|$fileName|${table.node.startOffset}"
}

class TyDocTable(val table: LuaDocTableDef) : TyClass(getDocTableTypeName(table)) {
    override fun doLazyInit(searchContext: SearchContext) {}

    override fun processMembers(context: SearchContext, processor: (ITyClass, LuaClassMember) -> Unit, deep: Boolean) {
        table.tableFieldList.forEach {
            processor(this, it)
        }
    }

    override fun findMember(name: String, searchContext: SearchContext): LuaClassMember? {
        return table.tableFieldList.firstOrNull { it.name == name }
    }
}

class TySerializedDocTable(name: String) : TySerializedClass(name) {
    override fun recoverAlias(context: SearchContext, aliasSubstitutor: TyAliasSubstitutor): ITy {
        return this
    }
}

object TyClassSerializer : TySerializer<ITyClass>() {
    override fun deserializeTy(flags: Int, stream: StubInputStream): ITyClass {
        val className = StringRef.toString(stream.readName())!!
        val varName = StringRef.toString(stream.readName())!!
        val aliasName = StringRef.toString(stream.readName())
        val superCount = stream.readVarInt()
        val superClassNames = (0 until superCount).mapNotNull { StringRef.toString(stream.readName()) }
        val implCount = stream.readVarInt()
        val implementsClassNames = (0 until implCount).mapNotNull { StringRef.toString(stream.readName()) }
        return createSerializedClass(className, varName, superClassNames, aliasName, flags, implementsClassNames)
    }

    override fun serializeTy(ty: ITyClass, stream: StubOutputStream) {
        stream.writeName(ty.className)
        stream.writeName(ty.varName)
        stream.writeName(ty.aliasName)
        val superNames = ty.superClassNames
        stream.writeVarInt(superNames.size)
        superNames.forEach { stream.writeName(it) }
        val implNames = ty.implementsClassNames
        stream.writeVarInt(implNames.size)
        implNames.forEach { stream.writeName(it) }
    }
}
