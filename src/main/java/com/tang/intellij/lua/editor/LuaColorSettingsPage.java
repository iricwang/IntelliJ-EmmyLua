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
package com.tang.intellij.lua.editor;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.tang.intellij.lua.highlighting.LuaHighlightingData;
import com.tang.intellij.lua.lang.LuaIcons;
import com.tang.intellij.lua.lang.LuaLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * Color Settings Page
 * Created by TangZX on 2017/1/9.
 */
public class LuaColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] ourAttributeDescriptors = new AttributesDescriptor[]{
            new AttributesDescriptor("Keywords", LuaHighlightingData.KEYWORD),
            new AttributesDescriptor("self", LuaHighlightingData.SELF),
            new AttributesDescriptor("String", LuaHighlightingData.STRING),
            new AttributesDescriptor("nil/true/false", LuaHighlightingData.PRIMITIVE_TYPE),
            new AttributesDescriptor("Number", LuaHighlightingData.NUMBER),
            new AttributesDescriptor("Braces and Operators//Operators", LuaHighlightingData.OPERATORS),
            new AttributesDescriptor("Braces and Operators//Brackets", LuaHighlightingData.BRACKETS),
            new AttributesDescriptor("Braces and Operators//Braces", LuaHighlightingData.BRACES),
            new AttributesDescriptor("Braces and Operators//Parentheses", LuaHighlightingData.PARENTHESES),
            new AttributesDescriptor("Braces and Operators//Semicolon", LuaHighlightingData.SEMICOLON),
            new AttributesDescriptor("Braces and Operators//Comma", LuaHighlightingData.COMMA),
            new AttributesDescriptor("Braces and Operators//Dot", LuaHighlightingData.DOT),
            new AttributesDescriptor("Variables//Parameter", LuaHighlightingData.PARAMETER),
            new AttributesDescriptor("Variables//Local variable", LuaHighlightingData.LOCAL_VAR),
            new AttributesDescriptor("Variables//Local function", LuaHighlightingData.LOCAL_FUNCTION),
            new AttributesDescriptor("Variables//Global variable", LuaHighlightingData.GLOBAL_VAR),
            new AttributesDescriptor("Variables//Global function", LuaHighlightingData.GLOBAL_FUNCTION),
            new AttributesDescriptor("Variables//Up value", LuaHighlightingData.UP_VALUE),
            new AttributesDescriptor("Comments//Line comment", LuaHighlightingData.LINE_COMMENT),
            new AttributesDescriptor("Comments//Doc comment", LuaHighlightingData.DOC_COMMENT),
            new AttributesDescriptor("Comments//EmmyDoc//Tag", LuaHighlightingData.DOC_COMMENT_TAG),
            new AttributesDescriptor("Comments//EmmyDoc//Tag value", LuaHighlightingData.DOC_COMMENT_TAG_VALUE),
            new AttributesDescriptor("Comments//EmmyDoc//Class name", LuaHighlightingData.CLASS_NAME),
            new AttributesDescriptor("Comments//EmmyDoc//Class name reference", LuaHighlightingData.CLASS_REFERENCE),
            new AttributesDescriptor("Comments//EmmyDoc//Type alias", LuaHighlightingData.TYPE_ALIAS),
            new AttributesDescriptor("Comments//EmmyDoc//Keyword", LuaHighlightingData.DOC_KEYWORD),
            new AttributesDescriptor("Region//Region header", LuaHighlightingData.REGION_HEADER),
            new AttributesDescriptor("Region//Region description", LuaHighlightingData.REGION_DESC),
            new AttributesDescriptor("Class members//Field", LuaHighlightingData.FIELD),
            new AttributesDescriptor("Class Members//Instance method", LuaHighlightingData.INSTANCE_METHOD),
            new AttributesDescriptor("Class Members//Static method", LuaHighlightingData.STATIC_METHOD),
            new AttributesDescriptor("Std api", LuaHighlightingData.STD_API)
    };

    @NonNls
    private static final Map<String, TextAttributesKey> ourTags = RainbowHighlighter.createRainbowHLM();

    static {
        ourTags.put("parameter", LuaHighlightingData.PARAMETER);
        ourTags.put("docTag", LuaHighlightingData.DOC_COMMENT_TAG);
        ourTags.put("docTagValue", LuaHighlightingData.DOC_COMMENT_TAG_VALUE);
        ourTags.put("docClassName", LuaHighlightingData.CLASS_NAME);
        ourTags.put("docClassNameRef", LuaHighlightingData.CLASS_REFERENCE);
        ourTags.put("docTypeAlias", LuaHighlightingData.TYPE_ALIAS);
        ourTags.put("docKeyword", LuaHighlightingData.DOC_KEYWORD);
        ourTags.put("localVar", LuaHighlightingData.LOCAL_VAR);
        ourTags.put("localFunction", LuaHighlightingData.LOCAL_FUNCTION);
        ourTags.put("globalVar", LuaHighlightingData.GLOBAL_VAR);
        ourTags.put("globalFunction", LuaHighlightingData.GLOBAL_FUNCTION);
        ourTags.put("field", LuaHighlightingData.FIELD);
        ourTags.put("method", LuaHighlightingData.INSTANCE_METHOD);
        ourTags.put("staticMethod", LuaHighlightingData.STATIC_METHOD);
        ourTags.put("upValue", LuaHighlightingData.UP_VALUE);
        ourTags.put("std", LuaHighlightingData.STD_API);
        ourTags.put("self", LuaHighlightingData.SELF);
        ourTags.put("primitive", LuaHighlightingData.PRIMITIVE_TYPE);
        ourTags.put("regionHeader", LuaHighlightingData.REGION_HEADER);
        ourTags.put("regionDesc", LuaHighlightingData.REGION_DESC);
    }

    @Override
    @Nullable
    public Icon getIcon() {
        return LuaIcons.FILE;
    }

    @Override
    @NotNull
    public SyntaxHighlighter getHighlighter() {
        return SyntaxHighlighterFactory.getSyntaxHighlighter(LuaLanguage.INSTANCE, null, null);
    }

    @Override
    @NotNull
    public String getDemoText() {
        return "---@class <docClassName>Emmy</docClassName>\n" +
                "local <localVar>var</localVar> = {} -- a short comment\n" +
                "local <localVar>a</localVar>, <localVar>b</localVar>, <localVar>c</localVar> = <primitive>true</primitive>, <primitive>false</primitive>, <primitive>nil</primitive>\n" +
                "<regionHeader>--region</regionHeader> <regionDesc>my class members region</regionDesc>\n" +
                "\n" +
                "---@alias <docTypeAlias>MyType</docTypeAlias> <docClassNameRef>Emmy</docClassNameRef>\n" +
                "\n" +
                "--- doc comment\n" +
                "---@param <docTagValue>par1</docTagValue> <docClassNameRef>Par1Type</docClassNameRef> @comments\n" +
                "function var:<method>fun</method>(<parameter>par1</parameter>, <parameter>par2</parameter>)\n" +
                "   <std>print</std>('hello')\n" +
                "   return <self>self</self>.<field>len</field> + 2\n" +
                "end\n" +
                "\n" +
                "---@overload <docKeyword>fun</docKeyword>(name:<docClassNameRef>string</docClassNameRef>):<docClassNameRef>Emmy</docClassNameRef>\n" +
                "function var.<staticMethod>staticFun</staticMethod>()\n" +
                "end\n" +
                "<regionHeader>--endregion</regionHeader> <regionDesc>end my class members region</regionDesc>\n" +
                "\n" +
                "---@return <docClassNameRef>Emmy</docClassNameRef>\n" +
                "function <globalFunction>findEmmy</globalFunction>()\n" +
                "   return \"string\" .. <upValue>var</upValue>\n" +
                "end\n" +
                "\n" +
                "<globalVar>globalVar</globalVar> = {\n" +
                "   <field>property</field> = value\n" +
                "}";
    }

    @Override
    @Nullable
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return ourTags;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return ourAttributeDescriptors;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return new ColorDescriptor[0];
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return "Lua";
    }
}
