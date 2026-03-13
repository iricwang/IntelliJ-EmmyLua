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

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.tang.intellij.lua.lang.LuaLanguage;
import com.tang.intellij.lua.project.LuaSettings;
import com.tang.intellij.lua.psi.LuaIndentRange;
import com.tang.intellij.lua.psi.LuaTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 回车时的自动补全
 * Created by tangzx on 2016/11/26.
 */
public class LuaEnterAfterUnmatchedBraceHandler implements EnterHandlerDelegate {

    private boolean shouldSmartIndent = false;

    private IElementType getEnd(IElementType range) {
        if (range == LuaTypes.TABLE_EXPR) return LuaTypes.RCURLY;
        return range == LuaTypes.REPEAT_STAT ? LuaTypes.UNTIL : LuaTypes.END;
    }

    @Override
    @NotNull
    public Result preprocessEnter(@NotNull PsiFile psiFile, @NotNull Editor editor, @NotNull Ref<Integer> caretOffsetRef,
                                  @NotNull Ref<Integer> caretAdvance, @NotNull DataContext dataContext,
                                  @Nullable EditorActionHandler editorActionHandler) {
        if (!psiFile.getLanguage().is(LuaLanguage.INSTANCE)) return Result.Continue;
        if (!LuaSettings.instance.isSmartCloseEnd()) return Result.Continue;

        int caretOffset = caretOffsetRef.get();
        PsiElement lElement = psiFile.findElementAt(caretOffset - 1);
        PsiElement rElement = psiFile.findElementAt(caretOffset);

        if (lElement != null && lElement != rElement) {
            boolean shouldClose = false;
            PsiElement range = null;
            PsiElement cur = lElement;
            while (true) {
                PsiElement searched = cur.getParent();
                if (searched == null || searched instanceof PsiFile) break;
                if (searched instanceof LuaIndentRange) {
                    IElementType endType = getEnd(searched.getNode().getElementType());
                    var endChild = searched.getNode().findChildByType(endType);
                    if (endChild == null) {
                        shouldClose = true;
                        range = searched;
                        break;
                    }
                }
                cur = searched;
            }

            if (shouldClose && range != null) {
                IElementType endType = getEnd(range.getNode().getElementType());
                var document = editor.getDocument();
                if (!(rElement instanceof PsiWhiteSpace)) {
                    document.insertString(caretOffset, endType.toString() + " ");
                } else {
                    document.insertString(caretOffset, endType.toString());
                }
                if (editorActionHandler != null) {
                    editorActionHandler.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
                }

                var project = lElement.getProject();
                PsiDocumentManager.getInstance(project).commitDocument(document);
                shouldSmartIndent = true;
                return Result.DefaultForceIndent;
            }
        }
        return Result.Continue;
    }

    @Override
    @NotNull
    public Result postProcessEnter(@NotNull PsiFile psiFile, @NotNull Editor editor, @NotNull DataContext dataContext) {
        if (!psiFile.getLanguage().is(LuaLanguage.INSTANCE)) return Result.Continue;

        if (shouldSmartIndent) {
            shouldSmartIndent = false;
            var project = editor.getProject();
            if (project == null) return Result.Continue;
            var document = editor.getDocument();
            PsiDocumentManager.getInstance(project).commitDocument(document);
            int caretOffset = editor.getCaretModel().getOffset();
            LuaIndentRange newRange = PsiTreeUtil.findElementOfClassAtOffset(psiFile, caretOffset, LuaIndentRange.class, false);
            if (newRange != null) {
                var textRange = newRange.getTextRange();
                var marker = document.createRangeMarker(textRange);
                ApplicationManager.getApplication().runWriteAction(() -> {
                    var styleManager = CodeStyleManager.getInstance(editor.getProject());
                    styleManager.adjustLineIndent(psiFile, textRange);
                    int endLine = document.getLineNumber(marker.getEndOffset());
                    int lineEnd = document.getLineEndOffset(endLine - 1);
                    editor.getCaretModel().moveToOffset(lineEnd);
                    styleManager.adjustLineIndent(psiFile, lineEnd);
                });
            }
        }
        return Result.Continue;
    }
}
