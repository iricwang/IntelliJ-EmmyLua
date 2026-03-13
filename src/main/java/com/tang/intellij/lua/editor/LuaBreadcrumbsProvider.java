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

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.tang.intellij.lua.lang.LuaLanguage;
import com.tang.intellij.lua.psi.*;
import org.jetbrains.annotations.NotNull;

public class LuaBreadcrumbsProvider implements BreadcrumbsProvider {

    private static final int MAX_LEN = 15;

    @Override
    public Language[] getLanguages() {
        return new Language[]{LuaLanguage.INSTANCE};
    }

    private String cutText(String txt) {
        if (txt.length() > MAX_LEN) {
            return txt.substring(0, MAX_LEN) + "...";
        }
        return txt;
    }

    @Override
    @NotNull
    public String getElementInfo(@NotNull PsiElement element) {
        if (element instanceof LuaBlock) {
            LuaBlock luaBlock = (LuaBlock) element;
            PsiElement blockParent = luaBlock.getParent();
            if (blockParent instanceof LuaFuncBody) {
                LuaFuncBody funcBody = (LuaFuncBody) blockParent;
                PsiElement parent2 = funcBody.getParent();
                if (parent2 instanceof LuaClassMethodDef) {
                    LuaClassMethodDef cmd = (LuaClassMethodDef) parent2;
                    return cmd.getClassMethodName().getText() + cmd.getParamSignature();
                } else if (parent2 instanceof LuaClosureExpr) {
                    LuaClosureExpr ce = (LuaClosureExpr) parent2;
                    return "function" + ce.getParamSignature();
                } else if (parent2 instanceof LuaFuncDef) {
                    LuaFuncDef fd = (LuaFuncDef) parent2;
                    return "function" + fd.getParamSignature();
                } else if (parent2 instanceof LuaLocalFuncDef) {
                    LuaLocalFuncDef lfd = (LuaLocalFuncDef) parent2;
                    return "local function " + lfd.getName();
                } else {
                    return "<?>";
                }
            } else if (blockParent instanceof LuaIfStat) {
                PsiElement prevVisibleLeaf = PsiTreeUtil.prevVisibleLeaf(element);
                if (prevVisibleLeaf != null && prevVisibleLeaf.getNode().getElementType() == LuaTypes.ELSE) {
                    return "else";
                } else if (prevVisibleLeaf != null && prevVisibleLeaf.getNode().getElementType() == LuaTypes.THEN) {
                    PsiElement expr = LuaPsiTreeUtil.skipWhitespacesAndCommentsBackward(prevVisibleLeaf);
                    PsiElement prefix = LuaPsiTreeUtil.skipWhitespacesAndCommentsBackward(expr);
                    return prefix.getText() + " " + cutText(expr.getText()) + " then";
                } else {
                    return "if";
                }
            } else if (blockParent instanceof LuaForAStat) {
                return "for";
            } else if (blockParent instanceof LuaForBStat) {
                return "for";
            } else if (blockParent instanceof LuaRepeatStat) {
                return "repeat";
            } else if (blockParent instanceof LuaWhileStat) {
                return "while";
            } else {
                return "<?>";
            }
        } else {
            return element.getText();
        }
    }

    @Override
    public boolean acceptElement(@NotNull PsiElement element) {
        return element instanceof LuaBlock;
    }
}
