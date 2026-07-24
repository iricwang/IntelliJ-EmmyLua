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

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 把单个 XML 类型定义文件转译为 EmmyLua 注释文本片段。
 *
 * 两种风格：
 * - RPC 风格（root 含 Aliases/S2CRpcs/C2SRpcs/S2SRpcs 段）→ 委托 [RpcXmlTranspiler]。
 * - alias/struct 风格（alias_parts 一类）→ 本类 [appendDef] 处理：
 *     - 有子元素 → `---@class Name` + 每个子元素一行 `---@field public <name> <type>`。
 *     - 无子元素但有 `Type` → `---@alias Name <type>`。
 *     - 类型映射 [mapType]：`array Element=X` → `table<number,X>`；`map Key=K Value=V` → `table<K,V>`；
 *       其它 → `Type` 原值。
 *
 * XML 行尾的 `<!-- 注释 -->` 在解析前由 [injectComments] 注入为该行所有标签的 [COMMENT_ATTR]
 * 属性（DOM 会丢弃注释节点），struct 字段据此生成 `---@field public x T @注释`。
 */
object XmlToLuaTranspiler {

    /** 注释预处理注入的属性名（对齐旧 Python 生成器的 _SOURCE_COMMENT 做法）。 */
    const val COMMENT_ATTR = "_SOURCE_COMMENT"

    /** 旧生成器硬编码的 4 个基础别名（不在任何 XML 里），每次重建注入一次并登记去重。 */
    private val BASE_ALIASES = listOf(
        "UINT" to "number",
        "INT" to "number",
        "SIMPLE_TABLE" to "table",
        "simplelua" to "table",
    )

    /** 生成基础别名片段；[emittedDefs] 非空时同时登记，避免后续 XML 重名定义覆盖。 */
    fun baseAliasesSnippet(emittedDefs: MutableSet<String>? = null): String {
        val sb = StringBuilder()
        for ((name, ty) in BASE_ALIASES) {
            if (emittedDefs != null && !emittedDefs.add(name)) continue
            sb.append("---@alias ").append(name).append(' ').append(ty).append('\n')
        }
        return sb.toString()
    }

    /**
     * 转译一个 XML 文本，返回 EmmyLua 文本片段；解析失败或无内容返回空串。
     *
     * @param moduleName XML 文件名（无扩展名），RPC 风格用它作为生成 class 的模块前缀。
     * @param gamePlay 是否 game_play 目录来源（RPC 风格决定 Client 的 sender 字段名）。
     * @param emittedDefs 非空时按定义名全局去重（先见先得，对齐旧生成器 GLOBAL_ALIAS_TYPE：
     *        alias_parts 先处理，RPC 模块 Aliases 段里与之重名的定义直接跳过）。
     */
    fun transpile(
        xmlText: String,
        moduleName: String,
        gamePlay: Boolean = false,
        emittedDefs: MutableSet<String>? = null,
    ): String {
        val doc = parse(injectComments(xmlText)) ?: return ""
        val root = doc.documentElement ?: return ""
        if (RpcXmlTranspiler.isRpcRoot(root)) {
            return RpcXmlTranspiler.transpile(root, moduleName, gamePlay, emittedDefs)
        }
        val sb = StringBuilder()
        for (el in elementChildren(root)) {
            if (el.tagName == "Enum") appendEnum(sb, el, emittedDefs) else appendDef(sb, el, emittedDefs)
        }
        return sb.toString()
    }

    /**
     * alias/struct 定义转译。空行布局对齐旧生成器模板：
     * alias 行前后不留空；struct 前留一空行（模板前导 \n）、结尾留一空行。
     */
    fun appendDef(sb: StringBuilder, el: Element, emittedDefs: MutableSet<String>? = null) {
        val name = el.tagName
        val childElements = elementChildren(el)
        if (childElements.isEmpty()) {
            // 别名：无子元素时依赖自身 Type；没有 Type 的空标签直接跳过（跳过前不得登记去重，
            // 否则 entities.xml 里的空声明会挤掉 entity_props 的同名实体类）。
            val ty = mapType(el) ?: return
            if (emittedDefs != null && !emittedDefs.add(name)) return
            sb.append("---@alias ").append(name).append(' ').append(ty).append('\n')
        } else {
            // 结构体
            if (emittedDefs != null && !emittedDefs.add(name)) return
            sb.append("\n---@class ").append(name).append('\n')
            for (child in childElements) {
                appendField(sb, child)
            }
            sb.append('\n')
        }
    }

    /** 单条字段行渲染：`---@field public <tag> <type> [@注释]`（struct 与实体 Properties 段共用）。 */
    fun appendField(sb: StringBuilder, el: Element) {
        val ty = mapType(el) ?: "any"
        sb.append("---@field public ").append(el.tagName).append(' ').append(ty)
        val comment = commentOf(el)
        if (comment.isNotEmpty()) sb.append(" @").append(comment)
        sb.append('\n')
    }

    /** 读取 [injectComments] 注入的行尾注释。 */
    fun commentOf(el: Element): String = el.getAttribute(COMMENT_ATTR).trim()

    /**
     * 枚举转译：`<Enum Name="N"><Value Name="V" code="C"/> <!--注释--></Enum>` →
     * 文档注释块（枚举注释 + 每个值一行 `V = C 注释`）+ `---@alias N integer`。
     *
     * 说明：运行时枚举值经手写 define 表（如 tradeDef.TRADE_ITEM_STATUS.X）消费，XML 枚举
     * 仅作为 rpc 参数类型被引用，故注解只需让类型名可解析、hover 可见值列表。
     * 参与 [emittedDefs] 去重（按枚举名，先见先得）。
     */
    fun appendEnum(sb: StringBuilder, el: Element, emittedDefs: MutableSet<String>? = null) {
        val name = el.getAttribute("Name").takeIf { it.isNotEmpty() } ?: return
        if (emittedDefs != null && !emittedDefs.add(name)) return
        sb.append('\n')
        val enumComment = commentOf(el)
        if (enumComment.isNotEmpty()) sb.append("--- ").append(enumComment).append('\n')
        for (value in elementChildren(el)) {
            val valueName = value.getAttribute("Name").takeIf { it.isNotEmpty() } ?: continue
            sb.append("--- ").append(valueName)
            val code = value.getAttribute("code").trim()
            if (code.isNotEmpty()) sb.append(" = ").append(code)
            val comment = commentOf(value)
            if (comment.isNotEmpty()) sb.append(' ').append(comment)
            sb.append('\n')
        }
        sb.append("---@alias ").append(name).append(" integer\n\n")
    }

    /** 依据 Type/Element/Key/Value 属性推导 EmmyLua 类型串；无 Type 返回 null。 */
    private fun mapType(el: Element): String? {
        val type = el.getAttribute("Type").takeIf { it.isNotEmpty() } ?: return null
        return when (type) {
            "array" -> {
                val elem = el.getAttribute("Element").ifEmpty { "any" }
                "table<number,$elem>"
            }
            "map" -> {
                val key = el.getAttribute("Key").ifEmpty { "any" }
                val value = el.getAttribute("Value").ifEmpty { "any" }
                "table<$key,$value>"
            }
            else -> type
        }
    }

    private val COMMENT_REGEX = Regex("<!--(.*?)-->")
    private val FIRST_TAG_REGEX = Regex("<(\\w+)")

    /**
     * 把每行的 `<!-- 注释 -->` 注入为该行所有标签的 [COMMENT_ATTR] 属性
     * （对齐旧生成器 parseXml 的 re.sub 全量替换：一行多个标签时每个都带注释）。
     * 纯注释行（无标签）保持原样，注释随 DOM 解析丢弃。
     */
    private fun injectComments(xmlText: String): String {
        if ("<!--" !in xmlText) return xmlText
        return xmlText.lineSequence().joinToString("\n") { line ->
            if ("<!--" !in line) return@joinToString line
            val m = COMMENT_REGEX.find(line) ?: return@joinToString line
            val comment = m.groupValues[1].trim().replace("\"", "").replace("'", "")
            val matches = FIRST_TAG_REGEX.findAll(line).toList()
            if (matches.isEmpty()) return@joinToString line
            val sb = StringBuilder(line.length + 64)
            var last = 0
            for (tm in matches) {
                sb.append(line, last, tm.range.first)
                sb.append('<').append(tm.groupValues[1]).append(' ')
                    .append(COMMENT_ATTR).append("=\"").append(comment).append('"')
                last = tm.range.last + 1
            }
            sb.append(line, last, line.length)
            sb.toString()
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

    private fun parse(xmlText: String): Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            // 安全：禁止 DTD/外部实体，避免 XXE。
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            val builder = factory.newDocumentBuilder()
            builder.parse(ByteArrayInputStream(xmlText.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            null
        }
    }
}
