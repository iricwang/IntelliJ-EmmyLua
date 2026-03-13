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

package com.tang.intellij.lua.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.tang.intellij.lua.comment.psi.LuaDocElementType;
import com.tang.intellij.lua.comment.psi.LuaDocTypes;
import com.tang.intellij.lua.comment.psi.impl.LuaCommentImpl;
import com.tang.intellij.lua.lexer.LuaLexerAdapter;
import com.tang.intellij.lua.parser.LuaParser;
import com.tang.intellij.lua.psi.*;
import com.tang.intellij.lua.stubs.LuaFileElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by tangzx on 2015/11/15.
 * Email:love.tangzx@qq.com
 */
public class LuaParserDefinition implements ParserDefinition {

    public static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
    public static final TokenSet COMMENTS = TokenSet.create(
            LuaTypes.SHORT_COMMENT,
            LuaTypes.BLOCK_COMMENT,
            LuaTypes.DOC_COMMENT,
            LuaTypes.REGION,
            LuaTypes.ENDREGION
    );
    public static final TokenSet STRINGS = TokenSet.create(LuaTypes.STRING);
    public static final TokenSet KEYWORD_TOKENS = TokenSet.create(
            LuaTypes.AND,
            LuaTypes.BREAK,
            LuaTypes.DO,
            LuaTypes.ELSE,
            LuaTypes.ELSEIF,
            LuaTypes.END,
            LuaTypes.FOR,
            LuaTypes.FUNCTION,
            LuaTypes.IF,
            LuaTypes.IN,
            LuaTypes.LOCAL,
            LuaTypes.NOT,
            LuaTypes.OR,
            LuaTypes.REPEAT,
            LuaTypes.RETURN,
            LuaTypes.THEN,
            LuaTypes.UNTIL,
            LuaTypes.WHILE,
            // lua5.3
            LuaTypes.DOUBLE_COLON,
            LuaTypes.GOTO
    );
    public static final TokenSet LUA52_BIN_OP_SET = TokenSet.create(
            LuaTypes.BIT_AND,
            LuaTypes.BIT_LTLT,
            LuaTypes.BIT_OR,
            LuaTypes.BIT_RTRT,
            LuaTypes.BIT_TILDE,
            LuaTypes.DOUBLE_DIV
    );
    public static final TokenSet LUA52_UNARY_OP_SET = TokenSet.create(
            LuaTypes.BIT_TILDE
    );
    public static final TokenSet PRIMITIVE_TYPE_SET = TokenSet.create(
            LuaTypes.FALSE,
            LuaTypes.NIL,
            LuaTypes.TRUE
    );
    public static final TokenSet DOC_TAG_TOKENS = TokenSet.create(
            LuaDocTypes.TAG_NAME_PARAM,
            LuaDocTypes.TAG_NAME_RETURN,
            LuaDocTypes.TAG_NAME_CLASS,
            LuaDocTypes.TAG_NAME_MODULE,
            LuaDocTypes.TAG_NAME_TYPE,
            LuaDocTypes.TAG_NAME_FIELD,
            LuaDocTypes.TAG_NAME_LANGUAGE,
            LuaDocTypes.TAG_NAME_OVERLOAD,
            LuaDocTypes.TAG_NAME_PRIVATE,
            LuaDocTypes.TAG_NAME_PROTECTED,
            LuaDocTypes.TAG_NAME_PUBLIC,
            LuaDocTypes.TAG_NAME_SEE,
            LuaDocTypes.TAG_NAME_GENERIC,
            LuaDocTypes.TAG_NAME_VARARG,
            LuaDocTypes.TAG_NAME_ALIAS
    );
    public static final TokenSet DOC_KEYWORD_TOKENS = TokenSet.create(
            LuaDocTypes.FUN,
            LuaDocTypes.VARARG
    );
    public static final LuaFileElementType FILE = new LuaFileElementType();

    @Override
    @NotNull
    public Lexer createLexer(Project project) {
        return new LuaLexerAdapter();
    }

    @Override
    @NotNull
    public TokenSet getWhitespaceTokens() {
        return WHITE_SPACES;
    }

    @Override
    @NotNull
    public TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @Override
    @NotNull
    public TokenSet getStringLiteralElements() {
        return STRINGS;
    }

    @Override
    @NotNull
    public PsiParser createParser(Project project) {
        return new LuaParser();
    }

    @Override
    @NotNull
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    @NotNull
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new LuaPsiFile(viewProvider);
    }

    @Override
    @NotNull
    public PsiElement createElement(@NotNull ASTNode node) {
        IElementType type = node.getElementType();
        if (type == LuaElementType.DOC_COMMENT) {
            return new LuaCommentImpl(node);
        }
        if (type instanceof LuaDocElementType
                || type == LuaElementType.DOC_TABLE_DEF
                || type == LuaElementType.DOC_TABLE_FIELD_DEF
                || type == LuaElementType.CLASS_DEF
                || type == LuaElementType.CLASS_FIELD_DEF
                || type == LuaElementType.TYPE_DEF
                || type == LuaElementType.DOC_ALIAS) {
            return LuaDocTypes.Factory.createElement(node);
        }
        return LuaTypes.Factory.createElement(node);
    }

    public static IElementType createType(String string) {
        switch (string) {
            case "FUNC_DEF": return LuaElementType.FUNC_DEF;
            case "CLASS_METHOD_DEF": return LuaElementType.CLASS_METHOD_DEF;
            case "BLOCK": return LuaElementType.BLOCK;
            case "TABLE_EXPR": return LuaElementType.TABLE;
            case "TABLE_FIELD": return LuaElementType.TABLE_FIELD;
            case "INDEX_EXPR": return LuaElementType.INDEX;
            case "NAME_EXPR": return LuaElementType.NAME_EXPR;
            case "NAME_DEF": return LuaElementType.NAME_DEF;
            case "PARAM_NAME_DEF": return LuaElementType.PARAM_NAME_DEF;
            case "LITERAL_EXPR": return LuaElementType.LITERAL_EXPR;
            case "CALL_EXPR": return LuaElementTypes.CALL_EXPR;
            case "SINGLE_ARG": return LuaElementTypes.SINGLE_ARG;
            case "LIST_ARGS": return LuaElementTypes.LIST_ARGS;
            case "EXPR_LIST": return LuaElementTypes.EXPR_LIST;
            case "NAME_LIST": return LuaElementTypes.NAME_LIST;
            case "LOCAL_DEF": return LuaElementTypes.LOCAL_DEF;
            case "ASSIGN_STAT": return LuaElementTypes.ASSIGN_STAT;
            case "VAR_LIST": return LuaElementTypes.VAR_LIST;
            case "PAREN_EXPR": return LuaElementTypes.PAREN_EXPR;
            case "LOCAL_FUNC_DEF": return LuaElementTypes.LOCAL_FUNC_DEF;
            case "CLOSURE_EXPR": return LuaElementTypes.CLOSURE_EXPR;
            case "FUNC_BODY": return LuaElementTypes.FUNC_BODY;
            case "CLASS_METHOD_NAME": return LuaElementTypes.CLASS_METHOD_NAME;
            case "RETURN_STAT": return LuaElementTypes.RETURN_STAT;
            case "DO_STAT": return LuaElementTypes.DO_STAT;
            case "IF_STAT": return LuaElementTypes.IF_STAT;
            case "EXPR_STAT": return LuaElementTypes.EXPR_STAT;
            case "UNARY_EXPR": return LuaElementTypes.UNARY_EXPR;
            case "BINARY_EXPR": return LuaElementTypes.BINARY_EXPR;
            default: return new LuaElementType(string);
        }
    }

    public static IElementType createToken(String string) {
        if ("DOC_COMMENT".equals(string)) return LuaElementType.DOC_COMMENT;
        return new LuaTokenType(string);
    }

    public static IElementType createDocType(String string) {
        switch (string) {
            case "TAG_CLASS": return LuaElementType.CLASS_DEF;
            case "TAG_FIELD": return LuaElementType.CLASS_FIELD_DEF;
            case "TABLE_DEF": return LuaElementType.DOC_TABLE_DEF;
            case "TABLE_FIELD": return LuaElementType.DOC_TABLE_FIELD_DEF;
            case "TAG_ALIAS": return LuaElementType.DOC_ALIAS;
            default: return "TAG_TYPE".equals(string) ? LuaElementType.TYPE_DEF : new LuaDocElementType(string);
        }
    }
}
