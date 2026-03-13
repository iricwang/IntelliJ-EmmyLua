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

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.psi.LuaFileUtil;
import com.tang.intellij.lua.psi.LuaPsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LuaQualifiedNameProvider implements QualifiedNameProvider {

    @Override
    @Nullable
    public PsiElement adjustElementToCopy(@NotNull PsiElement psiElement) {
        return null;
    }

    @Override
    @Nullable
    public String getQualifiedName(@NotNull PsiElement psiElement) {
        if (psiElement instanceof LuaPsiFile) {
            var virtualFile = ((LuaPsiFile) psiElement).getVirtualFile();
            var project = psiElement.getProject();
            return LuaFileUtil.asRequirePath(project, virtualFile);
        }
        return null;
    }

    @Override
    @Nullable
    public PsiElement qualifiedNameToElement(@NotNull String s, @NotNull Project project) {
        return null;
    }

    @Override
    public void insertQualifiedName(@NotNull String s, @NotNull PsiElement psiElement, @NotNull Editor editor, @NotNull Project project) {
    }
}
