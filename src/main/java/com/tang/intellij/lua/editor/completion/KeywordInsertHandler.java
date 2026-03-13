/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 * Licensed under the Apache License, Version 2.0
 */
package com.tang.intellij.lua.editor.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.openapi.document.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.tang.intellij.lua.codeInsight.template.macro.SuggestLuaParametersMacro;
import com.tang.intellij.lua.psi.LuaClosureExpr;
import com.tang.intellij.lua.psi.LuaIndentRange;
import com.tang.intellij.lua.psi.LuaTypes;

public class KeywordInsertHandler implements InsertHandler<LookupElement> {
    private final IElementType keyWordToken;

    KeywordInsertHandler(IElementType keyWordToken) {
        this.keyWordToken = keyWordToken;
    }

    @Override
    public void handleInsert(InsertionContext insertionContext, LookupElement lookupElement) {
        PsiFile file = insertionContext.getFile();
        Project project = insertionContext.getProject();
        Document document = insertionContext.getDocument();
        int offset = insertionContext.getTailOffset();
        if (keyWordToken == LuaTypes.FUNCTION) {
            PsiElement element = file.findElementAt(insertionContext.getStartOffset());
            if (element != null && element.getParent() instanceof LuaClosureExpr) {
                TemplateManager templateManager = TemplateManager.getInstance(project);
                com.intellij.codeInsight.template.Template template = templateManager.createTemplate("", "", "($PARAMETERS$) $END$ end");
                template.addVariable("PARAMETERS", new MacroCallNode(new SuggestLuaParametersMacro(SuggestLuaParametersMacro.Position.KeywordInsertHandler)), new TextExpression(""), false);
                templateManager.startTemplate(insertionContext.getEditor(), template);
            }
        } else {
            PsiElement element = file.findElementAt(offset);
            if (element != null && !(element instanceof PsiWhiteSpace)) {
                document.insertString(insertionContext.getTailOffset(), " ");
                insertionContext.getEditor().getCaretModel().moveToOffset(insertionContext.getTailOffset());
            }
        }
        autoIndent(keyWordToken, file, project, document, offset);
    }

    public static void autoIndent(IElementType keyWordToken, PsiFile file, Project project, Document document, int offset) {
        if (keyWordToken == LuaTypes.END || keyWordToken == LuaTypes.ELSE || keyWordToken == LuaTypes.ELSEIF) {
            PsiDocumentManager.getInstance(project).commitDocument(document);
            LuaIndentRange element = PsiTreeUtil.findElementOfClassAtOffset(file, offset, LuaIndentRange.class, false);
            if (element != null) {
                CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
                styleManager.adjustLineIndent(file, element.getTextRange());
            }
        }
    }
}
