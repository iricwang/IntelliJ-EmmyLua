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

package com.tang.intellij.lua.comment.psi;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.tang.intellij.lua.comment.LuaCommentUtil;
import com.tang.intellij.lua.comment.reference.LuaClassNameReference;
import com.tang.intellij.lua.comment.reference.LuaDocParamNameReference;
import com.tang.intellij.lua.comment.reference.LuaDocSeeReference;
import com.tang.intellij.lua.psi.LuaClassMember;
import com.tang.intellij.lua.psi.LuaElementFactory;
import com.tang.intellij.lua.psi.Visibility;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by TangZX on 2016/11/24.
 */
public class LuaDocPsiImplUtil {

    public static PsiReference getReference(LuaDocParamNameRef paramNameRef) {
        return new LuaDocParamNameReference(paramNameRef);
    }

    public static PsiReference getReference(LuaDocClassNameRef docClassNameRef) {
        return new LuaClassNameReference(docClassNameRef);
    }

    public static ITy resolveType(LuaDocClassNameRef nameRef) {
        return Ty.create(nameRef.getText());
    }

    @Nullable
    public static String getName(PsiNameIdentifierOwner identifierOwner) {
        PsiElement id = identifierOwner.getNameIdentifier();
        return id != null ? id.getText() : null;
    }

    public static PsiElement setName(PsiNameIdentifierOwner identifierOwner, String newName) {
        PsiElement oldId = identifierOwner.getNameIdentifier();
        if (oldId != null) {
            PsiElement newId = LuaElementFactory.createDocIdentifier(identifierOwner.getProject(), newName);
            oldId.replace(newId);
            return newId;
        }
        return identifierOwner;
    }

    public static int getTextOffset(PsiNameIdentifierOwner identifierOwner) {
        PsiElement id = identifierOwner.getNameIdentifier();
        return id != null ? id.getTextOffset() : identifierOwner.getNode().getStartOffset();
    }

    @Nullable
    public static PsiElement getNameIdentifier(LuaDocTagField tagField) {
        return tagField.getId();
    }

    @NotNull
    public static PsiElement getNameIdentifier(LuaDocTagClass tagClass) {
        return tagClass.getId();
    }

    @NotNull
    public static ITy guessType(LuaDocTagField tagField, SearchContext context) {
        com.tang.intellij.lua.stubs.LuaDocTagFieldStub stub = tagField.getStub();
        if (stub != null) return stub.getType();
        LuaDocTy ty = tagField.getTy();
        return ty != null ? ty.getType() : Ty.UNKNOWN;
    }

    @NotNull
    public static ITy guessParentType(LuaDocTagField tagField, SearchContext context) {
        PsiElement parent = tagField.getParent();
        LuaDocTagClass classDef = PsiTreeUtil.findChildOfType(parent, LuaDocTagClass.class);
        return classDef != null ? classDef.getType() : Ty.UNKNOWN;
    }

    @NotNull
    public static Visibility getVisibility(LuaDocTagField tagField) {
        com.tang.intellij.lua.stubs.LuaDocTagFieldStub stub = tagField.getStub();
        if (stub != null) return stub.getVisibility();
        LuaDocAccessModifier accessModifier = tagField.getAccessModifier();
        if (accessModifier != null) {
            return Visibility.get(accessModifier.getText());
        }
        return Visibility.PUBLIC;
    }

    /**
     * 猜测参数的类型
     * @param tagParamDec 参数定义
     * @return 类型集合
     */
    @NotNull
    public static ITy getType(LuaDocTagParam tagParamDec) {
        LuaDocTy docTy = tagParamDec.getTy();
        if (docTy != null) {
            ITy type = docTy.getType();
            TySubstitutor substitutor = LuaCommentUtil.INSTANCE.findContainer(tagParamDec).createSubstitutor();
            if (substitutor != null) return type.substitute(substitutor);
            return type;
        }
        return Ty.UNKNOWN;
    }

    @NotNull
    public static ITy getType(LuaDocTagVararg vararg) {
        LuaDocTy ty = vararg.getTy();
        return ty != null ? ty.getType() : Ty.UNKNOWN;
    }

    @NotNull
    public static ITy getType(LuaDocVarargParam vararg) {
        LuaDocTy ty = vararg.getTy();
        return ty != null ? ty.getType() : Ty.UNKNOWN;
    }

    /**
     * 获取返回类型
     * @param tagReturn 返回定义
     * @return 类型集合
     */
    @NotNull
    public static ITy resolveTypeAt(LuaDocTagReturn tagReturn, int index) {
        LuaDocTypeList typeList = tagReturn.getTypeList();
        if (typeList != null) {
            List<LuaDocTy> list = typeList.getTyList();
            if (list.size() > index) {
                return list.get(index).getType();
            }
        }
        return Ty.UNKNOWN;
    }

    @NotNull
    public static ITy getType(LuaDocTagReturn tagReturn) {
        LuaDocTypeList typeList = tagReturn.getTypeList();
        if (typeList != null) {
            List<LuaDocTy> tyList = typeList.getTyList();
            if (!tyList.isEmpty()) {
                List<ITy> tupleList = new ArrayList<>();
                for (LuaDocTy ty : tyList) {
                    tupleList.add(ty.getType());
                }
                return tupleList.size() == 1 ? tupleList.get(0) : new TyTuple(tupleList);
            }
        }
        return Ty.VOID;
    }

    /**
     * 优化：从stub中取名字
     * @param tagClass LuaDocClassDef
     * @return string
     */
    @NotNull
    public static String getName(LuaDocTagClass tagClass) {
        com.tang.intellij.lua.stubs.LuaDocTagClassStub stub = tagClass.getStub();
        if (stub != null) return stub.getClassName();
        return tagClass.getId().getText();
    }

    /**
     * for Goto Class
     * @param tagClass class def
     * @return ItemPresentation
     */
    @NotNull
    public static ItemPresentation getPresentation(LuaDocTagClass tagClass) {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return tagClass.getName();
            }

            @Override
            public String getLocationString() {
                return tagClass.getContainingFile().getName();
            }

            @Override
            public Icon getIcon(boolean b) {
                return AllIcons.Nodes.Class;
            }
        };
    }

    @NotNull
    public static ITyClass getType(LuaDocTagClass tagClass) {
        com.tang.intellij.lua.stubs.LuaDocTagClassStub stub = tagClass.getStub();
        if (stub != null) return stub.getClassType();
        return new TyPsiDocClass(tagClass);
    }

    public static boolean isDeprecated(LuaDocTagClass tagClass) {
        com.tang.intellij.lua.stubs.LuaDocTagClassStub stub = tagClass.getStub();
        if (stub != null) return stub.isDeprecated();
        return LuaCommentUtil.INSTANCE.findContainer(tagClass).isDeprecated();
    }

    /**
     * 猜测类型
     * @param tagType 类型定义
     * @return 类型集合
     */
    @NotNull
    public static ITy getType(LuaDocTagType tagType) {
        LuaDocTy ty = tagType.getTy();
        return ty != null ? ty.getType() : Ty.UNKNOWN;
    }

    @NotNull
    public static String toString(StubBasedPsiElement<? extends StubElement<?>> stubElement) {
        return "[STUB]";
    }

    @Nullable
    public static String getName(LuaDocTagField tagField) {
        com.tang.intellij.lua.stubs.LuaDocTagFieldStub stub = tagField.getStub();
        if (stub != null) return stub.getName();
        return getName((PsiNameIdentifierOwner) tagField);
    }

    @Nullable
    public static String getFieldName(LuaDocTagField tagField) {
        com.tang.intellij.lua.stubs.LuaDocTagFieldStub stub = tagField.getStub();
        if (stub != null) return stub.getName();
        return tagField.getName();
    }

    @NotNull
    public static ItemPresentation getPresentation(LuaDocTagField tagField) {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return tagField.getName();
            }

            @Override
            public String getLocationString() {
                return tagField.getContainingFile().getName();
            }

            @Override
            public Icon getIcon(boolean b) {
                return AllIcons.Nodes.Field;
            }
        };
    }

    @NotNull
    public static ITy getType(LuaDocArrTy luaDocArrTy) {
        ITy baseTy = luaDocArrTy.getTy().getType();
        return new TyArray(baseTy);
    }

    @NotNull
    public static ITy getType(LuaDocGeneralTy luaDocGeneralTy) {
        return resolveType(luaDocGeneralTy.getClassNameRef());
    }

    @NotNull
    public static ITy getType(LuaDocFunctionTy luaDocFunctionTy) {
        return new TyDocPsiFunction(luaDocFunctionTy);
    }

    @NotNull
    public static ITy getReturnType(LuaDocFunctionTy luaDocFunctionTy) {
        LuaDocTypeList typeListNode = luaDocFunctionTy.getTypeList();
        if (typeListNode == null) return Ty.VOID;
        List<LuaDocTy> tyList = typeListNode.getTyList();
        if (tyList.isEmpty()) return Ty.VOID;
        List<ITy> list = new ArrayList<>();
        for (LuaDocTy ty : tyList) {
            list.add(ty.getType());
        }
        return list.size() == 1 ? list.get(0) : new TyTuple(list);
    }

    @NotNull
    public static ITy getType(LuaDocGenericTy luaDocGenericTy) {
        return new TyDocGeneric(luaDocGenericTy);
    }

    @NotNull
    public static ITy getType(LuaDocParTy luaDocParTy) {
        LuaDocTy ty = luaDocParTy.getTy();
        return ty != null ? ty.getType() : Ty.UNKNOWN;
    }

    @NotNull
    public static ITy getType(LuaDocStringLiteralTy stringLiteral) {
        String text = stringLiteral.getText();
        String content = text.length() >= 2 ? text.substring(1, text.length() - 1) : "";
        return new TyStringLiteral(content);
    }

    @NotNull
    public static ITy getType(LuaDocUnionTy unionTy) {
        List<LuaDocTy> list = unionTy.getTyList();
        ITy retTy = Ty.UNKNOWN;
        for (LuaDocTy ty : list) {
            retTy = retTy.union(ty.getType());
        }
        return retTy;
    }

    @Nullable
    public static PsiReference getReference(LuaDocTagSee see) {
        if (see.getId() == null) return null;
        return new LuaDocSeeReference(see);
    }

    @NotNull
    public static ITy getType(LuaDocTableTy tbl) {
        return new TyDocTable(tbl.getTableDef());
    }

    @NotNull
    public static ITy guessParentType(LuaDocTableField f, SearchContext context) {
        LuaDocTableDef p = (LuaDocTableDef) f.getParent();
        return new TyDocTable(p);
    }

    @NotNull
    public static Visibility getVisibility(LuaDocTableField f) {
        return Visibility.PUBLIC;
    }

    public static int getWorth(LuaClassMember m) {
        return LuaClassMember.WORTH_DOC;
    }

    @Nullable
    public static PsiElement getNameIdentifier(LuaDocTableField f) {
        return f.getId();
    }

    @NotNull
    public static String getName(LuaDocTableField f) {
        com.tang.intellij.lua.stubs.LuaDocTableFieldStub stub = f.getStub();
        if (stub != null) return stub.getName();
        return f.getId().getText();
    }

    @NotNull
    public static ITy guessType(LuaDocTableField f, SearchContext context) {
        com.tang.intellij.lua.stubs.LuaDocTableFieldStub stub = f.getStub();
        ITy ty;
        if (stub != null) {
            ty = stub.getDocTy();
        } else {
            LuaDocTy docTy = f.getTy();
            ty = docTy != null ? docTy.getType() : null;
        }
        return ty != null ? ty : Ty.UNKNOWN;
    }

    @Nullable
    public static PsiElement getNameIdentifier(LuaDocGenericDef g) {
        return g.getId();
    }

    public static boolean isDeprecated(LuaClassMember member) {
        return false;
    }

    @Nullable
    public static PsiElement getNameIdentifier(LuaDocTagAlias g) {
        return g.getId();
    }

    @NotNull
    public static ITy getType(LuaDocTagAlias alias) {
        com.tang.intellij.lua.stubs.LuaDocTagAliasStub stub = alias.getStub();
        ITy ty = stub != null ? stub.getType() : null;
        if (ty == null) {
            LuaDocTy docTy = alias.getTy();
            ty = docTy != null ? docTy.getType() : null;
        }
        return ty != null ? ty : Ty.UNKNOWN;
    }
}
