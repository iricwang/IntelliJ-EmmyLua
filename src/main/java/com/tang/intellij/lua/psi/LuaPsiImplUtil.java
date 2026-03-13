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

package com.tang.intellij.lua.psi;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.tang.intellij.lua.comment.LuaCommentUtil;
import com.tang.intellij.lua.comment.psi.LuaDocAccessModifier;
import com.tang.intellij.lua.comment.psi.LuaDocTagVararg;
import com.tang.intellij.lua.comment.psi.api.LuaComment;
import com.tang.intellij.lua.lang.LuaIcons;
import com.tang.intellij.lua.lang.type.LuaString;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.stubs.LuaClassMemberStub;
import com.tang.intellij.lua.stubs.LuaFuncBodyOwnerStub;
import com.tang.intellij.lua.ty.*;
import com.tang.intellij.lua.ty.DeclarationsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class LuaPsiImplUtil {

    public static PsiElement setName(PsiNameIdentifierOwner owner, String name) {
        PsiElement oldId = owner.getNameIdentifier();
        if (oldId != null) {
            PsiElement newId = LuaElementFactory.createIdentifier(owner.getProject(), name);
            oldId.replace(newId);
            return newId;
        }
        return owner;
    }

    @NotNull
    public static PsiElement getNameIdentifier(LuaNameDef nameDef) {
        return nameDef.getFirstChild();
    }

    /**
     * LuaNameDef 只可能在本文件中搜
     * @param nameDef def
     * @return SearchScope
     */
    @NotNull
    public static SearchScope getUseScope(LuaNameDef nameDef) {
        return GlobalSearchScope.fileScope(nameDef.getContainingFile());
    }

    @NotNull
    public static PsiReference[] getReferences(LuaPsiElement element) {
        return ReferenceProvidersRegistry.getReferencesFromProviders(element, PsiReferenceService.Hints.NO_HINTS);
    }

    /**
     * 寻找 Comment
     * @param declaration owner
     * @return LuaComment
     */
    @Nullable
    public static LuaComment getComment(LuaCommentOwner declaration) {
        return LuaCommentUtil.INSTANCE.findComment(declaration);
    }

    @Nullable
    public static PsiElement getNameIdentifier(LuaClassMethodDef classMethodDef) {
        return classMethodDef.getClassMethodName().getId();
    }

    @Nullable
    public static String getName(LuaClassMethodDef classMethodDef) {
        com.tang.intellij.lua.stubs.LuaClassMethodStub stub = classMethodDef.getStub();
        if (stub != null) return stub.getName();
        return getName((PsiNameIdentifierOwner) classMethodDef);
    }

    public static boolean isStatic(LuaClassMethodDef classMethodDef) {
        com.tang.intellij.lua.stubs.LuaClassMethodStub stub = classMethodDef.getStub();
        if (stub != null) return stub.isStatic();
        return classMethodDef.getClassMethodName().getDot() != null;
    }

    @NotNull
    public static ItemPresentation getPresentation(LuaClassMethodDef classMethodDef) {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                ITy type = LuaClassMemberKt.guessClassType(classMethodDef, SearchContext.Companion.get(classMethodDef.getProject()));
                if (type != null) {
                    String c = classMethodDef.isStatic() ? "." : ":";
                    return type.getDisplayName() + c + classMethodDef.getName() + classMethodDef.getParamSignature();
                }
                return classMethodDef.getName() + classMethodDef.getParamSignature();
            }

            @Override
            public String getLocationString() {
                return classMethodDef.getContainingFile().getName();
            }

            @Override
            public Icon getIcon(boolean b) {
                return LuaIcons.CLASS_METHOD;
            }
        };
    }

    /**
     * 寻找对应的类
     * @param classMethodDef def
     * @return LuaType
     */
    @NotNull
    public static ITy guessParentType(LuaClassMethodDef classMethodDef, SearchContext context) {
        LuaExpr expr = classMethodDef.getClassMethodName().getExpr();
        ITy ty = expr.guessType(context);
        ITyClass perfect = TyUnion.Companion.getPerfectClass(ty);
        return perfect != null ? perfect : Ty.UNKNOWN;
    }

    @Nullable
    public static PsiElement getNameIdentifier(LuaFuncDef funcDef) {
        return funcDef.getId();
    }

    @Nullable
    public static String getName(LuaFuncDef funcDef) {
        com.tang.intellij.lua.stubs.LuaFuncStub stub = funcDef.getStub();
        if (stub != null) return stub.getName();
        return getName((PsiNameIdentifierOwner) funcDef);
    }

    @NotNull
    public static ItemPresentation getPresentation(LuaFuncDef funcDef) {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                String name = funcDef.getName();
                return name == null ? null : name + funcDef.getParamSignature();
            }

            @Override
            public String getLocationString() {
                return funcDef.getContainingFile().getName();
            }

            @Override
            public Icon getIcon(boolean b) {
                return AllIcons.Nodes.Function;
            }
        };
    }

    @NotNull
    public static ITyClass guessParentType(LuaFuncDef funcDef, SearchContext searchContext) {
        return TyClass.Companion.getG();
    }

    /**
     * 猜出前面的类型
     * @param callExpr call expr
     * @return LuaType
     */
    @NotNull
    public static ITy guessParentType(LuaCallExpr callExpr, SearchContext context) {
        return callExpr.getExpr().guessType(context);
    }

    /**
     * 获取第一个字符串参数
     * @param callExpr callExpr
     * @return String PsiElement
     */
    @Nullable
    public static PsiElement getFirstStringArg(LuaCallExpr callExpr) {
        LuaArgs args = callExpr.getArgs();
        PsiElement path = null;

        if (args instanceof LuaSingleArg) {
            LuaExpr expr = ((LuaSingleArg) args).getExpr();
            if (expr instanceof LuaLiteralExpr) path = expr;
        } else if (args instanceof LuaListArgs) {
            List<LuaExpr> list = ((LuaListArgs) args).getExprList();
            if (!list.isEmpty() && list.get(0) instanceof LuaLiteralExpr) {
                LuaLiteralExpr valueExpr = (LuaLiteralExpr) list.get(0);
                if (valueExpr.getKind() == LuaLiteralKind.String) path = valueExpr;
            }
        }
        return path;
    }

    public static boolean isMethodDotCall(LuaCallExpr callExpr) {
        LuaExpr expr = callExpr.getExpr();
        if (expr instanceof LuaNameExpr) return true;
        return expr instanceof LuaIndexExpr && ((LuaIndexExpr) expr).getColon() == null;
    }

    public static boolean isMethodColonCall(LuaCallExpr callExpr) {
        LuaExpr expr = callExpr.getExpr();
        return expr instanceof LuaIndexExpr && ((LuaIndexExpr) expr).getColon() != null;
    }

    public static boolean isFunctionCall(LuaCallExpr callExpr) {
        return callExpr.getExpr() instanceof LuaNameExpr;
    }

    @Nullable
    public static LuaExpr at(LuaExprList list, int index) {
        List<LuaExpr> exprList = PsiTreeUtil.getStubChildrenOfTypeAsList(list, LuaExpr.class);
        if (index < exprList.size()) return exprList.get(index);
        return exprList.isEmpty() ? null : exprList.get(exprList.size() - 1);
    }

    @NotNull
    public static ITy guessTypeAt(LuaExprList list, SearchContext context) {
        LuaExpr expr = at(list, context.getIndex());
        if (expr != null) {
            int index = context.getIndex();
            List<LuaExpr> exprList = PsiTreeUtil.getStubChildrenOfTypeAsList(list, LuaExpr.class);
            if (exprList.size() > 1) {
                int nameSize = context.getIndex() + 1;
                index = nameSize > exprList.size() ? nameSize - exprList.size() : 0;
            }
            final int finalIndex = index;
            final LuaExpr finalExpr = expr;
            return context.withIndex(finalIndex, () -> finalExpr.guessType(context));
        }
        return Ty.UNKNOWN;
    }

    @NotNull
    public static ITy guessParentType(LuaIndexExpr indexExpr, SearchContext context) {
        LuaExpr expr = PsiTreeUtil.getStubChildOfType(indexExpr, LuaExpr.class);
        return expr != null ? expr.guessType(context) : Ty.UNKNOWN;
    }

    @Nullable
    public static PsiElement getNameIdentifier(LuaIndexExpr indexExpr) {
        PsiElement id = indexExpr.getId();
        if (id != null) return id;
        return indexExpr.getIdExpr();
    }

    @NotNull
    public static ItemPresentation getPresentation(LuaIndexExpr indexExpr) {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return indexExpr.getName();
            }

            @Override
            public String getLocationString() {
                return indexExpr.getContainingFile().getName();
            }

            @Override
            public Icon getIcon(boolean b) {
                return LuaIcons.CLASS_FIELD;
            }
        };
    }

    /**
     * xx['id']
     */
    @Nullable
    public static LuaLiteralExpr getIdExpr(LuaIndexExpr indexExpr) {
        PsiElement bracket = indexExpr.getLbrack();
        if (bracket != null) {
            LuaExpr nextLeaf = PsiTreeUtil.getNextSiblingOfType(bracket, LuaExpr.class);
            if (nextLeaf instanceof LuaLiteralExpr && ((LuaLiteralExpr) nextLeaf).getKind() == LuaLiteralKind.String) {
                return (LuaLiteralExpr) nextLeaf;
            }
        }
        return null;
    }

    @Nullable
    public static String getName(LuaIndexExpr indexExpr) {
        com.tang.intellij.lua.stubs.LuaIndexExprStub stub = indexExpr.getStub();
        if (stub != null) return stub.getName();

        // var.name
        PsiElement id = indexExpr.getId();
        if (id != null) return id.getText();

        // var['name']
        LuaLiteralExpr idExpr = indexExpr.getIdExpr();
        if (idExpr != null) return LuaString.Companion.getContent(idExpr.getText()).getValue();

        return null;
    }

    @NotNull
    public static PsiElement setName(LuaIndexExpr indexExpr, String name) {
        if (indexExpr.getId() != null) return setName((PsiNameIdentifierOwner) indexExpr, name);
        LuaLiteralExpr idExpr = indexExpr.getIdExpr();
        if (idExpr != null) {
            String text = idExpr.getText();
            LuaString content = LuaString.Companion.getContent(text);
            String newText = text.substring(0, content.getStart()) + name + text.substring(content.getEnd());
            PsiElement newId = LuaElementFactory.createLiteral(indexExpr.getProject(), newText);
            return idExpr.replace(newId);
        }
        return indexExpr;
    }

    @Nullable
    public static LuaTableField findField(LuaTableExpr table, String fieldName) {
        for (LuaTableField field : table.getTableFieldList()) {
            if (fieldName.equals(field.getName())) return field;
        }
        return null;
    }

    @NotNull
    public static List<LuaParamNameDef> getParamNameDefList(LuaFuncBodyOwner funcBodyOwner) {
        LuaFuncBody funcBody = funcBodyOwner.getFuncBody();
        if (funcBody != null) return funcBody.getParamNameDefList();
        return Collections.emptyList();
    }

    @NotNull
    public static List<LuaParamNameDef> getParamNameDefList(LuaForAStat forAStat) {
        List<LuaParamNameDef> list = new ArrayList<>();
        list.add(forAStat.getParamNameDef());
        return list;
    }

    @NotNull
    public static ITy guessReturnType(LuaFuncBodyOwner owner, SearchContext searchContext) {
        return DeclarationsKt.inferReturnTy(owner, searchContext);
    }

    @Nullable
    public static ITy getVarargTy(LuaFuncBodyOwner owner) {
        if (owner instanceof StubBasedPsiElementBase) {
            StubElement<?> stub = ((StubBasedPsiElementBase<?>) owner).getStub();
            if (stub instanceof LuaFuncBodyOwnerStub) {
                return ((LuaFuncBodyOwnerStub<?>) stub).getVarargTy();
            }
        }
        LuaFuncBody funcBody = owner.getFuncBody();
        if (funcBody != null && funcBody.getEllipsis() != null) {
            ITy ret = null;
            if (owner instanceof LuaCommentOwner) {
                LuaComment comment = ((LuaCommentOwner) owner).getComment();
                if (comment != null) {
                    LuaDocTagVararg varargDef = comment.findTag(LuaDocTagVararg.class);
                    if (varargDef != null) ret = varargDef.getType();
                }
            }
            return ret != null ? ret : Ty.UNKNOWN;
        }
        return null;
    }

    @NotNull
    public static LuaParamInfo[] getParams(LuaFuncBodyOwner owner) {
        if (owner instanceof StubBasedPsiElementBase) {
            StubElement<?> stub = ((StubBasedPsiElementBase<?>) owner).getStub();
            if (stub instanceof LuaFuncBodyOwnerStub) {
                return ((LuaFuncBodyOwnerStub<?>) stub).getParams();
            }
        }
        return getParamsInner(owner);
    }

    private static LuaParamInfo[] getParamsInner(LuaFuncBodyOwner funcBodyOwner) {
        LuaComment comment = null;
        if (funcBodyOwner instanceof LuaCommentOwner) {
            comment = LuaCommentUtil.INSTANCE.findComment((LuaCommentOwner) funcBodyOwner);
        } else {
            LuaCommentOwner commentOwner = PsiTreeUtil.getParentOfType(funcBodyOwner, LuaCommentOwner.class);
            if (commentOwner != null) comment = LuaCommentUtil.INSTANCE.findComment(commentOwner);
        }

        List<LuaParamNameDef> paramNameList = funcBodyOwner.getParamNameDefList();
        if (paramNameList != null) {
            List<LuaParamInfo> list = new ArrayList<>();
            for (LuaParamNameDef paramNameDef : paramNameList) {
                LuaParamInfo paramInfo = new LuaParamInfo();
                String paramName = paramNameDef.getText();
                paramInfo.setName(paramName);
                if (comment != null) {
                    LuaDocTagParam paramDef = comment.getParamDef(paramName);
                    if (paramDef != null) {
                        paramInfo.setTy(paramDef.getType());
                    }
                }
                list.add(paramInfo);
            }
            return list.toArray(new LuaParamInfo[0]);
        }
        return new LuaParamInfo[0];
    }

    @NotNull
    public static String getParamSignature(LuaFuncBodyOwner funcBodyOwner) {
        LuaParamInfo[] params = funcBodyOwner.getParams();
        String[] list = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            list[i] = params[i].getName();
        }
        return "(" + String.join(", ", list) + ")";
    }

    @Nullable
    public static String getName(LuaLocalFuncDef localFuncDef) {
        com.tang.intellij.lua.stubs.LuaLocalFuncDefStub stub = localFuncDef.getStub();
        if (stub != null) return stub.getName();
        return getName((PsiNameIdentifierOwner) localFuncDef);
    }

    @Nullable
    public static PsiElement getNameIdentifier(LuaLocalFuncDef localFuncDef) {
        return localFuncDef.getId();
    }

    @NotNull
    public static SearchScope getUseScope(LuaLocalFuncDef localFuncDef) {
        return GlobalSearchScope.fileScope(localFuncDef.getContainingFile());
    }

    @NotNull
    public static String getName(LuaNameDef nameDef) {
        com.tang.intellij.lua.stubs.LuaNameDefStub stub = nameDef.getStub();
        if (stub != null) return stub.getName();
        return nameDef.getId().getText();
    }

    @Nullable
    public static String getName(PsiNameIdentifierOwner identifierOwner) {
        PsiElement id = identifierOwner.getNameIdentifier();
        return id != null ? id.getText() : null;
    }

    public static int getTextOffset(PsiNameIdentifierOwner localFuncDef) {
        PsiElement id = localFuncDef.getNameIdentifier();
        if (id != null) return id.getTextOffset();
        return localFuncDef.getNode().getStartOffset();
    }

    @Nullable
    public static PsiElement getNameIdentifier(LuaTableField tableField) {
        PsiElement id = tableField.getId();
        if (id != null) return id;
        return tableField.getIdExpr();
    }

    @NotNull
    public static ITy guessParentType(LuaTableField tableField, SearchContext context) {
        LuaTableExpr tbl = PsiTreeUtil.getParentOfType(tableField, LuaTableExpr.class);
        assert tbl != null;
        return tbl.guessType(context);
    }

    @NotNull
    public static ITy guessType(LuaTableField tableField, SearchContext context) {
        com.tang.intellij.lua.stubs.LuaTableFieldStub stub = tableField.getStub();
        // from comment
        ITy docTy;
        if (stub != null) {
            docTy = stub.getDocTy();
        } else {
            LuaComment comment = tableField.getComment();
            docTy = (comment != null && comment.getTagType() != null) ? comment.getTagType().getType() : null;
        }
        if (docTy != null) return docTy;

        // guess from value
        LuaExpr valueExpr = PsiTreeUtil.getStubChildOfType(tableField, LuaExpr.class);
        if (valueExpr != null) return valueExpr.guessType(context);
        return Ty.UNKNOWN;
    }

    @Nullable
    public static String getName(LuaTableField tableField) {
        com.tang.intellij.lua.stubs.LuaTableFieldStub stub = tableField.getStub();
        if (stub != null) return stub.getName();
        PsiElement id = tableField.getId();
        if (id != null) return id.getText();

        LuaExpr idExpr = tableField.getIdExpr();
        if (idExpr instanceof LuaLiteralExpr && ((LuaLiteralExpr) idExpr).getKind() == LuaLiteralKind.String) {
            return LuaString.Companion.getContent(idExpr.getText()).getValue();
        }
        return null;
    }

    @Nullable
    public static String getFieldName(LuaTableField tableField) {
        return getName(tableField);
    }

    @NotNull
    public static ItemPresentation getPresentation(LuaTableField tableField) {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return tableField.getName();
            }

            @Override
            public String getLocationString() {
                return tableField.getContainingFile().getName();
            }

            @Override
            public Icon getIcon(boolean b) {
                return LuaIcons.CLASS_FIELD;
            }
        };
    }

    /**
     * xx['id']
     */
    @Nullable
    public static LuaExpr getIdExpr(LuaTableField tableField) {
        PsiElement bracket = tableField.getLbrack();
        if (bracket != null) {
            return PsiTreeUtil.getNextSiblingOfType(bracket, LuaExpr.class);
        }
        return null;
    }

    @NotNull
    public static String toString(StubBasedPsiElement<? extends StubElement<?>> stubElement) {
        return "STUB:[" + stubElement.getClass().getSimpleName() + "]";
    }

    @NotNull
    public static ItemPresentation getPresentation(LuaNameExpr nameExpr) {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return nameExpr.getName();
            }

            @Override
            public String getLocationString() {
                return nameExpr.getContainingFile().getName();
            }

            @Override
            public Icon getIcon(boolean b) {
                return LuaIcons.CLASS_FIELD;
            }
        };
    }

    @NotNull
    public static PsiElement getNameIdentifier(LuaNameExpr ref) {
        return ref.getId();
    }

    @NotNull
    public static String getName(LuaNameExpr nameExpr) {
        com.tang.intellij.lua.stubs.LuaNameExprStub stub = nameExpr.getStub();
        if (stub != null) return stub.getName();
        return nameExpr.getText();
    }

    @NotNull
    public static ITy guessReturnType(@Nullable LuaReturnStat returnStat, int index, SearchContext context) {
        if (returnStat != null) {
            LuaExprList returnExpr = returnStat.getExprList();
            if (returnExpr != null) {
                return context.withIndex(index, () -> {
                    if (context.guessTuple()) return returnExpr.guessType(context);
                    else return returnExpr.guessTypeAt(context);
                });
            }
        }
        return Ty.UNKNOWN;
    }

    @Nullable
    public static PsiElement getNameIdentifier(LuaLabelStat label) {
        return label.getId();
    }

    @NotNull
    public static Visibility getVisibility(LuaClassMember member) {
        if (member instanceof StubBasedPsiElement) {
            StubElement<?> stub = ((StubBasedPsiElement<?>) member).getStub();
            if (stub instanceof LuaClassMemberStub) {
                return ((LuaClassMemberStub<?>) stub).getVisibility();
            }
        }
        if (member instanceof LuaCommentOwner) {
            LuaComment comment = ((LuaCommentOwner) member).getComment();
            if (comment != null) {
                LuaDocAccessModifier modifier = comment.findTag(LuaDocAccessModifier.class);
                if (modifier != null) return Visibility.get(modifier.getText());
            }
        }
        return Visibility.PUBLIC;
    }

    @NotNull
    public static Visibility getVisibility(LuaClassMethodDef classMethodDef) {
        return getVisibility((LuaClassMember) classMethodDef);
    }

    public static int getWorth(LuaClassMember classMember) {
        if (classMember instanceof LuaTableField) return LuaClassMember.WORTH_TABLE_FIELD;
        if (classMember instanceof LuaClassMethodDef || classMember instanceof LuaFuncDef)
            return LuaClassMember.WORTH_METHOD_DEF;
        return LuaClassMember.WORTH_ASSIGN;
    }

    @NotNull
    public static LuaExpr getExpr(LuaExprStat exprStat) {
        LuaExpr expr = PsiTreeUtil.getStubChildOfType(exprStat, LuaExpr.class);
        assert expr != null;
        return expr;
    }

    public static boolean isDeprecated(LuaClassMember member) {
        if (member instanceof StubBasedPsiElement) {
            StubElement<?> stub = ((StubBasedPsiElement<?>) member).getStub();
            if (stub instanceof LuaClassMemberStub) {
                return ((LuaClassMemberStub<?>) stub).isDeprecated();
            }
        }
        if (member instanceof LuaCommentOwner) {
            LuaComment comment = ((LuaCommentOwner) member).getComment();
            if (comment != null) return comment.isDeprecated();
        }
        return false;
    }
}
