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
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import com.tang.intellij.lua.lang.LuaLanguage;
import com.tang.intellij.lua.psi.LuaBlock;
import com.tang.intellij.lua.psi.LuaIndentRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LuaEnterBetweenRangeBlockHandler extends EnterHandlerDelegateAdapter {

    @Override
    @NotNull
    public EnterHandlerDelegate.Result preprocessEnter(@NotNull PsiFile psiFile, @NotNull Editor editor,
                                                       @NotNull Ref<Integer> caretOffsetRef, @NotNull Ref<Integer> caretAdvance,
                                                       @NotNull DataContext dataContext, @Nullable EditorActionHandler originalHandler) {
        if (!psiFile.getLanguage().is(LuaLanguage.INSTANCE)) return EnterHandlerDelegate.Result.Continue;

        int caretOffset = caretOffsetRef.get();
        var document = editor.getDocument();
        var text = document.getCharsSequence();
        int prevCharOffset = CharArrayUtil.shiftBackward(text, caretOffset - 1, " \t");
        int nextCharOffset = CharArrayUtil.shiftForward(text, caretOffset, " \t");

        var prev = psiFile.findElementAt(prevCharOffset);
        var next = psiFile.findElementAt(nextCharOffset);
        if (prev != next && prev != null && next != null
                && prev.getParent() == next.getParent()
                && prev.getParent() instanceof LuaIndentRange
                && prev.getNextSibling() instanceof LuaBlock) {
            if (originalHandler != null) {
                originalHandler.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
            }
            PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(editor.getDocument());
            try {
                CodeStyleManager.getInstance(psiFile.getProject()).adjustLineIndent(psiFile, editor.getCaretModel().getOffset());
            } catch (IncorrectOperationException ignored) {
            }
            return EnterHandlerDelegate.Result.DefaultForceIndent;
        }
        return EnterHandlerDelegate.Result.Continue;
    }
}
