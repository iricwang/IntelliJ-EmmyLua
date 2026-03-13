/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 * Licensed under the Apache License, Version 2.0
 */
package com.tang.intellij.lua.editor.completion;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.psi.*;
import com.tang.intellij.lua.ty.IFunSignature;
import com.tang.intellij.lua.ty.TyFunctionKt;

import java.util.ArrayList;
import java.util.List;

public class FunctionInsertHandler {
    // This file contains SignatureInsertHandler and SignatureInsertHandlerForString
}

class SignatureInsertHandler extends ArgsInsertHandler {
    protected final IFunSignature sig;
    private final boolean isColonStyle;
    private LuaParamInfo[] myParams;

    public SignatureInsertHandler(IFunSignature sig) {
        this(sig, false);
    }

    public SignatureInsertHandler(IFunSignature sig, boolean isColonStyle) {
        this.sig = sig;
        this.isColonStyle = isColonStyle;
    }

    @Override
    public LuaParamInfo[] getParams() {
        if (myParams == null) {
            List<LuaParamInfo> list = new ArrayList<>();
            TyFunctionKt.processArgs(sig, null, isColonStyle, (index, param) -> {
                list.add(param);
                return true;
            });
            myParams = list.toArray(new LuaParamInfo[0]);
        }
        return myParams;
    }

    @Override
    public boolean isVarargs() {
        return TyFunctionKt.hasVarargs(sig);
    }
}

class SignatureInsertHandlerForString extends SignatureInsertHandler {
    public SignatureInsertHandlerForString(IFunSignature sig) {
        super(sig, false);
    }

    public SignatureInsertHandlerForString(IFunSignature sig, boolean isColonStyle) {
        super(sig, isColonStyle);
    }

    @Override
    protected void appendSignature(InsertionContext insertionContext, Editor editor, PsiElement element) {
        int startOffset = insertionContext.getStartOffset();
        PsiElement at = insertionContext.getFile().findElementAt(startOffset);
        LuaIndexExpr indexExpr = at != null && at.getParent() instanceof LuaIndexExpr ? (LuaIndexExpr) at.getParent() : null;
        if (indexExpr != null) {
            LuaExpr prefixExpr = indexExpr.getPrefixExpr();
            if (prefixExpr instanceof LuaLiteralExpr && ((LuaLiteralExpr) prefixExpr).getKind() == LuaLiteralKind.String) {
                com.intellij.lang.ASTNode node = prefixExpr.getNode();
                insertionContext.getDocument().insertString(node.getStartOffset() + node.getTextLength(), ")");
                insertionContext.getDocument().insertString(node.getStartOffset(), "(");
                insertionContext.getOffsetMap().addOffset(CompletionInitializationContext.START_OFFSET, 2);
            }
        }
        super.appendSignature(insertionContext, editor, element);
    }
}
