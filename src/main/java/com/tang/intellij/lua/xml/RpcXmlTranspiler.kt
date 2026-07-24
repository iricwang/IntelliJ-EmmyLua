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

import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * 把 RPC 风格的 XML（含 Aliases/S2CRpcs/C2SRpcs/S2SRpcs 段）转译为 EmmyLua 文本。
 *
 * 输出布局以 LuaScripts/fakeApi/defs 现有产物为基准（对齐旧 Python 生成器 generator.py），
 * 不复刻其已知 bug（参数类型丢失、Enum 错位输出等）。模块名 module = XML 文件名（无扩展名）。
 *
 * S2CRpcs 段生成（方法首参注入为调用约定的一部分）：
 * - {M}ServerSender                      —— 每个 rpc 一个方法，首参 entityId，带 @see {M}Client#doXxx
 * - {M}ClientReceiver : {M}ClientReceiverImpl
 * - {M}ClientReceiverImpl                —— 每个 rpc 一个方法，首参 system: {M}Client
 * - {M}Server : {M}ServerImpl            —— 前置 `---@field public s2cSender {gamePlay:{M}ServerSender}`
 * - {M}ClientImpl                        —— 每个 rpc 一个 doXxx 实现方法（do + 首字母大写）
 *
 * C2SRpcs 段生成：
 * - {M}ServerReceiver : {M}ServerReceiverImpl
 * - {M}ServerReceiverImpl                —— 首参 system: {M}Server + entityId，带 @see {M}Server#doXxx
 * - {M}ServerMock : {M}ServerMockImpl
 * - {M}ServerMockImpl                    —— 首参 receiver: {M}ServerSender + entityId，无 @see
 * - {M}ClientSender                      —— 无首参，带 @see {M}Server#doXxx
 * - {M}Client : {M}ClientImpl            —— 前置 c2dsSender（game_play）/ c2lsSender（lobby）字段
 * - {M}ServerImpl                        —— 每个 rpc 一个 doXxx 实现方法，首参 entityId
 *
 * Aliases 段内的 struct/alias 复用 [XmlToLuaTranspiler.appendDef]，Enum 复用
 * [XmlToLuaTranspiler.appendEnum]（含 rpc 元素内联 Enum，见 [emitInlineEnums]）。
 *
 * 其余段：
 * - S2SRpcs → `{M}S2SRpc` 文档类（S2S 消息为字符串动态分发，无静态调用约定，见 [appendS2sGroup]）
 * - Properties + Implements（game_play/entity_props）→ 实体属性类（见 [appendProperties]）
 * - 纯 rpc 文件的 Implements 组合声明（如 BattlePlayer 聚合 26 个接口）不生成内容，与旧生成器一致。
 */
object RpcXmlTranspiler {

    private val SECTION_NAMES = setOf("Aliases", "S2CRpcs", "C2SRpcs", "S2SRpcs", "Implements", "Properties")

    /** 判断该 root 是否为 RPC 风格（含任一 RPC 段）。 */
    fun isRpcRoot(root: Element): Boolean =
        elementChildren(root).any { it.tagName in SECTION_NAMES }

    fun transpile(root: Element, module: String, gamePlay: Boolean, emittedDefs: MutableSet<String>?): String {
        val sb = StringBuilder()

        // Implements 段可能出现在任意段之前（entity_props 里固定在最前），先预扫描父接口列表。
        val parents = elementChildren(root)
            .firstOrNull { it.tagName == "Implements" }
            ?.let { impl -> elementChildren(impl).map { it.textContent.trim() }.filter { it.isNotEmpty() } }
            .orEmpty()

        // 按 XML 子段出现顺序生成（对齐旧生成器 parse_implement 的遍历行为：
        // C2SRpcs 在前的文件，C2S 组就排在 S2C 组前面）。
        for (child in elementChildren(root)) {
            when (child.tagName) {
                "Aliases" -> {
                    for (el in elementChildren(child)) {
                        if (el.tagName == "Enum") {
                            XmlToLuaTranspiler.appendEnum(sb, el, emittedDefs)
                        } else {
                            XmlToLuaTranspiler.appendDef(sb, el, emittedDefs)
                        }
                    }
                }
                "S2CRpcs" -> {
                    emitInlineEnums(sb, child, emittedDefs)
                    appendS2cGroup(sb, module, parseRpcs(child))
                }
                "C2SRpcs" -> {
                    emitInlineEnums(sb, child, emittedDefs)
                    appendC2sGroup(sb, module, gamePlay, parseRpcs(child))
                }
                "S2SRpcs" -> appendS2sGroup(sb, module, parseRpcs(child))
                "Properties" -> appendProperties(sb, module, parents, child, emittedDefs)
            }
        }
        return sb.toString()
    }

    /** 把该 RPC 段内 rpc 元素内联的 <Enum> 定义生成到组前（参与全局去重）。 */
    private fun emitInlineEnums(sb: StringBuilder, section: Element, emittedDefs: MutableSet<String>?) {
        for (rpc in elementChildren(section)) {
            for (sub in elementChildren(rpc)) {
                if (sub.tagName == "Enum") XmlToLuaTranspiler.appendEnum(sb, sub, emittedDefs)
            }
        }
    }

    /**
     * S2S 组：生成 `{M}S2SRpc` 文档类，每个消息一个方法。
     * 说明：S2S 消息经 `pg.callLobbyPlayer(gbId, "name", ...)` 等字符串动态分发
     * （见 server/lobby/lobby_proxy/ds_2_lobby_proxy.lua），Lua 侧无静态调用约定，
     * 此类仅作为参数文档与符号跳转目标，不映射具体 sender/receiver 语义。
     */
    private fun appendS2sGroup(sb: StringBuilder, m: String, rpcs: List<Rpc>) {
        classWithBody(sb, "${m}S2SRpc")
        for (rpc in rpcs) {
            appendParams(sb, emptyList(), rpc.children)
            appendFunction(sb, rpc.name, argNames(emptyList(), rpc))
        }
    }

    /**
     * Properties 段（game_play/entity_props）：生成实体属性类 `{module}`，
     * Implements 段的接口作为父类（单个；多接口时逗号分隔，依赖多继承扩展）。
     * Implements 与 Properties 均为空时不产出（如纯 rpc 文件的 Implements 组合声明不生成类）。
     */
    private fun appendProperties(
        sb: StringBuilder,
        module: String,
        parents: List<String>,
        section: Element,
        emittedDefs: MutableSet<String>?,
    ) {
        val fields = elementChildren(section)
        if (parents.isEmpty() && fields.isEmpty()) return
        if (emittedDefs != null && !emittedDefs.add(module)) return
        sb.append("\n---@class ").append(module)
        if (parents.isNotEmpty()) sb.append(" : ").append(parents.joinToString(", "))
        sb.append('\n')
        for (f in fields) {
            XmlToLuaTranspiler.appendField(sb, f)
        }
        sb.append('\n')
    }

    /** S2C 组：ServerSender → ClientReceiver(Impl) → Server（含 s2cSender 字段）→ ClientImpl。 */
    private fun appendS2cGroup(sb: StringBuilder, m: String, rpcs: List<Rpc>) {
        val entityId = RpcChild.Param("entityId", "number", "实体ID")

        classWithBody(sb, "${m}ServerSender")
        for (rpc in rpcs) {
            appendParams(sb, listOf(entityId), rpc.children)
            appendSee(sb, "${m}Client", doMethod(rpc.name))
            appendFunction(sb, rpc.name, argNames(listOf("entityId"), rpc))
        }

        sb.append("\n---@class ").append(m).append("ClientReceiver : ").append(m).append("ClientReceiverImpl\n")

        classWithBody(sb, "${m}ClientReceiverImpl")
        val system = RpcChild.Param("system", "${m}Client", "客户端系统")
        for (rpc in rpcs) {
            appendParams(sb, listOf(system), rpc.children)
            appendFunction(sb, rpc.name, argNames(listOf("system"), rpc))
        }

        sb.append("---@field public s2cSender {gamePlay:").append(m).append("ServerSender}\n")
        sb.append("---@class ").append(m).append("Server:").append(m).append("ServerImpl\n")

        classWithBody(sb, "${m}ClientImpl")
        for (rpc in rpcs) {
            appendParams(sb, emptyList(), rpc.children)
            // do-方法在手写代码里以冒号调用（system:doS2cXxx(...)），生成冒号风格才能通过
            // EmmyLua 冒号补全的 self 首参校验。
            appendFunction(sb, doMethod(rpc.name), argNames(emptyList(), rpc), colon = true)
        }
    }

    /** C2S 组：ServerReceiver(Impl) → ServerMock(Impl) → ClientSender → Client（含 sender 字段）→ ServerImpl。 */
    private fun appendC2sGroup(sb: StringBuilder, m: String, gamePlay: Boolean, rpcs: List<Rpc>) {
        val entityId = RpcChild.Param("entityId", "number", "实体ID")

        sb.append("\n---@class ").append(m).append("ServerReceiver : ").append(m).append("ServerReceiverImpl\n")

        classWithBody(sb, "${m}ServerReceiverImpl")
        val system = RpcChild.Param("system", "${m}Server", "服务端系统")
        for (rpc in rpcs) {
            appendParams(sb, listOf(system, entityId), rpc.children)
            appendSee(sb, "${m}Server", doMethod(rpc.name))
            appendFunction(sb, rpc.name, argNames(listOf("system", "entityId"), rpc))
        }

        sb.append("\n---@class ").append(m).append("ServerMock : ").append(m).append("ServerMockImpl\n")

        classWithBody(sb, "${m}ServerMockImpl")
        val receiver = RpcChild.Param("receiver", "${m}ServerSender", "服务端系统")
        for (rpc in rpcs) {
            appendParams(sb, listOf(receiver, entityId), rpc.children)
            appendFunction(sb, rpc.name, argNames(listOf("receiver", "entityId"), rpc))
        }

        classWithBody(sb, "${m}ClientSender")
        for (rpc in rpcs) {
            appendParams(sb, emptyList(), rpc.children)
            appendSee(sb, "${m}Server", doMethod(rpc.name))
            appendFunction(sb, rpc.name, argNames(emptyList(), rpc))
        }

        val senderField = if (gamePlay) "c2dsSender" else "c2lsSender"
        sb.append("---@field public ").append(senderField).append(' ').append(m).append("ClientSender\n")
        sb.append("---@class ").append(m).append("Client:").append(m).append("ClientImpl\n")

        classWithBody(sb, "${m}ServerImpl")
        for (rpc in rpcs) {
            appendParams(sb, listOf(entityId), rpc.children)
            // 同 ClientImpl：system:doC2sXxx(entityId, ...) 冒号调用约定。
            appendFunction(sb, doMethod(rpc.name), argNames(listOf("entityId"), rpc), colon = true)
        }
    }

    private fun classWithBody(sb: StringBuilder, name: String) {
        sb.append("\n---@class ").append(name).append('\n')
        sb.append("local m = {}\n")
    }

    /**
     * @param 行渲染：注入首参（固定名）在前，rpc 子元素在后。
     * - 参数子元素：输出 `---@param`；无名参数按全部子元素 0 起始编号
     *   （对齐旧生成器 write_system_method_params 的下标约定，与签名编号 [argNames] 不同）。
     * - Exposed 等标记子元素：只输出裸注释行（旧生成器的既有产物形态）。
     * - Enum 子元素：跳过（注解由 [emitInlineEnums] 在组前生成），但仍占编号位。
     */
    private fun appendParams(sb: StringBuilder, injected: List<RpcChild.Param>, children: List<RpcChild>) {
        for (p in injected) {
            sb.append("---@param ").append(p.name).append(' ').append(p.type)
            if (p.comment.isNotEmpty()) sb.append(" @").append(p.comment)
            sb.append('\n')
        }
        children.forEachIndexed { index, child ->
            when (child) {
                is RpcChild.Param -> {
                    sb.append("---@param ").append(child.name ?: "val$index").append(' ').append(child.type)
                    if (child.comment.isNotEmpty()) sb.append(" @").append(child.comment)
                    sb.append('\n')
                }
                is RpcChild.Marker -> if (child.comment.isNotEmpty()) {
                    sb.append("---").append(child.comment).append('\n')
                }
                RpcChild.Skip -> {}
            }
        }
    }

    private fun appendSee(sb: StringBuilder, className: String, method: String) {
        sb.append("---@see ").append(className).append('#').append(method).append('\n')
    }

    private fun appendFunction(sb: StringBuilder, name: String, args: List<String>, colon: Boolean = false) {
        sb.append("function m").append(if (colon) ':' else '.').append(name).append('(')
        sb.append(args.joinToString(", "))
        sb.append(") end\n\n")
    }

    /**
     * 方法签名实参列表：注入首参在前，rpc 参数在后（标记/Enum 子元素不进签名）。
     * 无名参数编号 = 注入首参数 + 参数子元素序号（对齐旧生成器 get_function_args 的下标约定）。
     */
    private fun argNames(injected: List<String>, rpc: Rpc): List<String> =
        injected + rpc.children.filterIsInstance<RpcChild.Param>()
            .mapIndexed { index, p -> p.name ?: "val${index + injected.size}" }

    /** 调用约定：实现方法名 = "do" + 首字母大写的 rpc 名（doS2cXxx / doC2sXxx）。 */
    private fun doMethod(rpcName: String): String =
        "do" + rpcName.replaceFirstChar { it.uppercaseChar() }

    /**
     * rpc 子元素解析：
     * - 普通参数（Arg 等）→ [RpcChild.Param]；
     * - Exposed 等标记元素 → [RpcChild.Marker]（不进签名，只可能输出裸注释）；
     * - 内联 Enum → [RpcChild.Skip]（注解由 [emitInlineEnums] 在组前生成；占位以维持无名参数编号）。
     */
    private fun parseRpcs(section: Element?): List<Rpc> {
        if (section == null) return emptyList()
        return elementChildren(section).map { rpcEl ->
            val children = elementChildren(rpcEl).map { p ->
                when (p.tagName) {
                    "Enum" -> RpcChild.Skip
                    "Exposed" -> RpcChild.Marker(XmlToLuaTranspiler.commentOf(p))
                    else -> RpcChild.Param(
                        p.getAttribute("Name").takeIf { it.isNotEmpty() },
                        paramType(p),
                        XmlToLuaTranspiler.commentOf(p)
                    )
                }
            }
            Rpc(rpcEl.tagName, children)
        }
    }

    /**
     * RPC 参数类型：`Type="array"` → `table<number,Element>`；`Type="map"` → `table<Key,Value>`；
     * 其它取元素文本（如 `<Arg Name="x"> UINT32 </Arg>` 的 UINT32）；文本为空回退 Type 属性，再回退 any。
     * （旧生成器对无注释的 array/map 参数错误地输出空类型，这里按语义修正。）
     */
    private fun paramType(el: Element): String {
        return when (val type = el.getAttribute("Type")) {
            "array" -> "table<number,${el.getAttribute("Element").ifEmpty { "any" }}>"
            "map" -> "table<${el.getAttribute("Key").ifEmpty { "any" }},${el.getAttribute("Value").ifEmpty { "any" }}>"
            else -> el.textContent.trim().ifEmpty { type.ifEmpty { "any" } }
        }
    }

    private fun elementChildren(el: Element): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = el.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) result.add(n as Element)
        }
        return result
    }

    private data class Rpc(val name: String, val children: List<RpcChild>)

    /** rpc 子元素模型，见 [parseRpcs]。 */
    private sealed interface RpcChild {
        data class Param(val name: String?, val type: String, val comment: String) : RpcChild
        data class Marker(val comment: String) : RpcChild
        data object Skip : RpcChild
    }
}
