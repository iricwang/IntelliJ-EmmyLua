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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.util.Processor;
import com.tang.intellij.lua.lang.LuaLanguage;
import com.tang.intellij.lua.psi.*;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LuaNameSuggestionProvider implements NameSuggestionProvider {

    public static String fixName(String oriName) {
        return oriName.replace(".", "");
    }

    @FunctionalInterface
    private interface NameCollector {
        void collect(String name, String suffix, boolean preferLonger);
    }

    private void collectNames(ITy type, SearchContext context, NameCollector collector) {
        if (type instanceof ITyClass) {
            ITyClass cls = (ITyClass) type;
            if (!cls.isAnonymous() && !(cls instanceof TyDocTable)) {
                collector.collect(fixName(cls.getClassName()), "", false);
            }
            TyClass.processSuperClass(cls, context, superType -> {
                if (!superType.isAnonymous()) {
                    collector.collect(fixName(superType.getClassName()), "", false);
                }
                return true;
            });
        } else if (type instanceof ITyArray) {
            ITyArray arr = (ITyArray) type;
            collectNames(arr.getBase(), context, (name, s, p) -> collector.collect(name, "List", false));
        } else if (type instanceof ITyGeneric) {
            ITyGeneric gen = (ITyGeneric) type;
            ITy paramTy = gen.getParamTy(1);
            collectNames(paramTy, context, (name, s, p) -> collector.collect(name, "Map", false));
        }
    }

    private void getNames(PsiReference ref, Set<String> set) {
        PsiElement ele = ref.getElement();
        PsiElement p1 = ele.getParent();
        if (ele instanceof LuaExpr) {
            LuaExpr luaExpr = (LuaExpr) ele;
            if (p1 instanceof LuaListArgs) {
                LuaListArgs listArgs = (LuaListArgs) p1;
                int paramIndex = listArgs.getIndexFor(luaExpr);
                if (p1.getParent() instanceof LuaCallExpr) {
                    LuaCallExpr callExpr = (LuaCallExpr) p1.getParent();
                    ITy ty = callExpr.guessParentType(SearchContext.get(ele.getProject()));
                    TyUnion.each(ty, iTy -> {
                        if (iTy instanceof ITyFunction) {
                            ITyFunction func = (ITyFunction) iTy;
                            func.process((Processor<IFunSignature>) sig -> {
                                IParamInfo paramInfo = sig.getParams().length > paramIndex ? sig.getParams()[paramIndex] : null;
                                if (paramInfo != null) {
                                    set.add(paramInfo.getName());
                                }
                                return false;
                            });
                        }
                    });
                }
            } else if (p1 instanceof LuaExprList) {
                if (p1.getParent() instanceof LuaAssignStat) {
                    LuaAssignStat assignStat = (LuaAssignStat) p1.getParent();
                    List<LuaExpr> valueList = assignStat.getValueExprList() != null ? assignStat.getValueExprList().getExprList() : null;
                    if (valueList != null) {
                        int index = valueList.indexOf(luaExpr);
                        LuaExpr varExpr = assignStat.getExprAt(index);
                        if (varExpr instanceof LuaIndexExpr) {
                            String name = ((LuaIndexExpr) varExpr).getName();
                            if (name != null) set.add(name);
                        }
                    }
                }
            }
        }
    }

    @Override
    @Nullable
    public SuggestedNameInfo getSuggestedNames(@NotNull PsiElement psi, @Nullable PsiElement nameSuggestionContext, @NotNull Set<String> set) {
        if (!(psi.getLanguage() instanceof LuaLanguage)) return null;
        var search = ReferencesSearch.search(psi, psi.getUseScope());
        search.forEach(ref -> getNames(ref, set));
        if (psi instanceof LuaTypeGuessable) {
            SearchContext context = SearchContext.get(psi.getProject());
            ITy type = ((LuaTypeGuessable) psi).guessType(context);
            if (!Ty.isInvalid(type)) {
                Set<String> names = new HashSet<>();
                TyUnion.each(type, ty -> collectNames(ty, context, (name, suffix, preferLonger) -> {
                    if (names.add(name)) {
                        String[] strings = NameUtil.getSuggestionsByName(name, "", suffix, false, preferLonger, false);
                        for (String s : strings) set.add(s);
                    }
                }));
            }
        }
        return null;
    }
}
