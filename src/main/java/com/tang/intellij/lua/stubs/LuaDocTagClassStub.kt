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

package com.tang.intellij.lua.stubs

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.io.StringRef
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.comment.psi.impl.LuaDocTagClassImpl
import com.tang.intellij.lua.psi.LuaElementType
import com.tang.intellij.lua.psi.aliasName
import com.tang.intellij.lua.psi.implementsNamesFromComment
import com.tang.intellij.lua.stubs.index.StubKeys
import com.tang.intellij.lua.ty.TyClass
import com.tang.intellij.lua.ty.createSerializedClass

/**
 * Created by tangzx on 2016/11/28.
 */
class LuaDocTagClassType : LuaStubElementType<LuaDocTagClassStub, LuaDocTagClass>("DOC_CLASS") {

    override fun createPsi(luaDocClassStub: LuaDocTagClassStub): LuaDocTagClass {
        return LuaDocTagClassImpl(luaDocClassStub, this)
    }

    override fun createStub(luaDocTagClass: LuaDocTagClass, stubElement: StubElement<*>): LuaDocTagClassStub {
        val superClassNames = luaDocTagClass.superClassNameRefList.map { it.text }
        val aliasName: String? = luaDocTagClass.aliasName
        val implementsClassNames = luaDocTagClass.implementsNamesFromComment()

        return LuaDocTagClassStubImpl(
            luaDocTagClass.name, aliasName, superClassNames, implementsClassNames,
            luaDocTagClass.isDeprecated, stubElement
        )
    }

    override fun serialize(luaDocClassStub: LuaDocTagClassStub, stubOutputStream: StubOutputStream) {
        stubOutputStream.writeName(luaDocClassStub.className)
        stubOutputStream.writeName(luaDocClassStub.aliasName)
        stubOutputStream.writeBoolean(luaDocClassStub.isDeprecated)

        val superNames = luaDocClassStub.superClassNames
        stubOutputStream.writeVarInt(superNames.size)
        superNames.forEach { stubOutputStream.writeName(it) }

        val implNames = luaDocClassStub.implementsClassNames
        stubOutputStream.writeVarInt(implNames.size)
        implNames.forEach { stubOutputStream.writeName(it) }
    }

    override fun deserialize(stubInputStream: StubInputStream, stubElement: StubElement<*>): LuaDocTagClassStub {
        val className = StringRef.toString(stubInputStream.readName())!!
        val aliasName = StringRef.toString(stubInputStream.readName())
        val isDeprecated = stubInputStream.readBoolean()

        val superCount = stubInputStream.readVarInt()
        val superClassNames = (0 until superCount).mapNotNull { StringRef.toString(stubInputStream.readName()) }

        val implCount = stubInputStream.readVarInt()
        val implementsClassNames = (0 until implCount).mapNotNull { StringRef.toString(stubInputStream.readName()) }

        return LuaDocTagClassStubImpl(className, aliasName, superClassNames, implementsClassNames, isDeprecated, stubElement)
    }

    override fun indexStub(luaDocClassStub: LuaDocTagClassStub, indexSink: IndexSink) {
        val classType = luaDocClassStub.classType
        indexSink.occurrence(StubKeys.CLASS, classType.className)
        indexSink.occurrence(StubKeys.SHORT_NAME, classType.className)

        for (superClassName in luaDocClassStub.superClassNames) {
            indexSink.occurrence(StubKeys.SUPER_CLASS, superClassName)
        }
        for (ifaceName in luaDocClassStub.implementsClassNames) {
            indexSink.occurrence(StubKeys.IMPLEMENTS_CLASS, ifaceName)
        }
    }
}

interface LuaDocTagClassStub : StubElement<LuaDocTagClass> {
    val className: String
    val aliasName: String?
    /** All direct parent class names (supports multiple inheritance). */
    val superClassNames: List<String>
    /** First parent class name; null if no parents. Kept for backward compatibility. */
    val superClassName: String? get() = superClassNames.firstOrNull()
    /** Interfaces this class explicitly implements via @implements. */
    val implementsClassNames: List<String>
    val classType: TyClass
    val isDeprecated: Boolean
}

class LuaDocTagClassStubImpl(override val className: String,
                             override val aliasName: String?,
                             override val superClassNames: List<String>,
                             override val implementsClassNames: List<String>,
                             override val isDeprecated: Boolean,
                             parent: StubElement<*>)
    : LuaDocStubBase<LuaDocTagClass>(parent, LuaElementType.CLASS_DEF), LuaDocTagClassStub {

    override val classType: TyClass
        get() {
            val luaType = createSerializedClass(className, className, superClassNames,
                implementsClassNames = implementsClassNames)
            luaType.aliasName = aliasName
            return luaType
        }
}
