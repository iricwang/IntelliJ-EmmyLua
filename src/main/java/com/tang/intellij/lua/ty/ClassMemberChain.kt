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

import com.tang.intellij.lua.psi.LuaClassMember

/**
 * Holds the members declared directly on [ty] and chains to zero or more
 * super-class chains (supporting multiple inheritance).
 */
class ClassMemberChain(val ty: ITyClass, val superChains: List<ClassMemberChain>) {

    /** Convenience accessor for single-inheritance code paths. */
    val superChain: ClassMemberChain? get() = superChains.firstOrNull()

    private val members = mutableMapOf<String, LuaClassMember>()

    fun add(member: LuaClassMember) {
        val name = member.name ?: return
        val superExist = findSuperMember(name)
        val override = superExist == null || canOverride(member, superExist)
        if (override) {
            val selfExist = members[name]
            if (selfExist == null || member.worth > selfExist.worth)
                members[name] = member
        }
    }

    /** Finds [name] in any of the super chains (first-parent wins). */
    private fun findSuperMember(name: String): LuaClassMember? {
        for (chain in superChains) {
            val m = chain.findMember(name)
            if (m != null) return m
        }
        return null
    }

    fun findMember(name: String): LuaClassMember? {
        return members[name] ?: findSuperMember(name)
    }

    private fun processInternal(deep: Boolean, cache: MutableSet<String>, processor: (ITyClass, LuaClassMember) -> Unit) {
        for ((name, member) in members) {
            if (cache.add(name)) {
                processor(ty, member)
            }
        }
        if (deep) {
            for (chain in superChains) {
                chain.processInternal(deep, cache, processor)
            }
        }
    }

    fun process(deep: Boolean, processor: (ITyClass, LuaClassMember) -> Unit) {
        val cache = mutableSetOf<String>()
        processInternal(deep, cache, processor)
    }

    private fun canOverride(member: LuaClassMember, superMember: LuaClassMember): Boolean {
        return member.worth > superMember.worth || (member.worth == superMember.worth && member.worth > LuaClassMember.WORTH_ASSIGN)
    }
}
