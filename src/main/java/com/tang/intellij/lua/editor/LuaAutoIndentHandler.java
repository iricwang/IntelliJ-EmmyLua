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

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.tang.intellij.lua.editor.completion.KeywordInsertHandler;
import com.tang.intellij.lua.lang.LuaFileType;
import org.jetbrains.annotations.NotNull;

/**
 * 当打出 then else elseif end 时自动缩进
 * Created by TangZX on 2016/12/20.
 */
public class LuaAutoIndentHandler extends TypedHandlerDelegate {
    @Override
    @NotNull
    public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (file.getFileType() == LuaFileType.INSTANCE) {
            var caretModel = editor.getCaretModel();
            EditorEx ex = (EditorEx) editor;
            var highlighter = ex.getHighlighter();
            var iterator = highlighter.createIterator(caretModel.getOffset() - 1);

            if (!iterator.atEnd()) {
                var type = iterator.getTokenType();
                KeywordInsertHandler.autoIndent(type, file, project, editor.getDocument(), caretModel.getOffset());
            }
        }
        return super.charTyped(c, project, editor, file);
    }
}
