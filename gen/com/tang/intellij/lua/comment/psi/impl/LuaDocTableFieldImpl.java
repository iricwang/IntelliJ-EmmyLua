// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.comment.psi.LuaDocTypes.*;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.tang.intellij.lua.stubs.LuaDocTableFieldStub;
import com.tang.intellij.lua.comment.psi.*;
import com.tang.intellij.lua.psi.Visibility;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaDocTableFieldImpl extends StubBasedPsiElementBase<LuaDocTableFieldStub> implements LuaDocTableField {

  public LuaDocTableFieldImpl(@NotNull LuaDocTableFieldStub stub, @NotNull IStubElementType<?, ?> type) {
    super(stub, type);
  }

  public LuaDocTableFieldImpl(@NotNull LuaDocTableFieldStub stub, @NotNull IElementType type) {
    super(stub, type);
  }

  public LuaDocTableFieldImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaDocTableFieldImpl(LuaDocTableFieldStub stub, IElementType type, ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaDocVisitor visitor) {
    visitor.visitTableField(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaDocVisitor) accept((LuaDocVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaDocTy getTy() {
    return PsiTreeUtil.getChildOfType(this, LuaDocTy.class);
  }

  @Override
  @NotNull
  public PsiElement getId() {
    return notNullChild(findChildByType(ID));
  }

  @Override
  public @NotNull ITy guessParentType(@NotNull SearchContext context) {
    return LuaDocPsiImplUtilKt.guessParentType(this, context);
  }

  @Override
  public @NotNull Visibility getVisibility() {
    return LuaDocPsiImplUtilKt.getVisibility(this);
  }

  @Override
  public int getWorth() {
    return LuaDocPsiImplUtilKt.getWorth(this);
  }

  @Override
  public @NotNull PsiElement setName(@NotNull String newName) {
    return LuaDocPsiImplUtilKt.setName(this, newName);
  }

  @Override
  public @Nullable String getName() {
    return LuaDocPsiImplUtilKt.getName(this);
  }

  @Override
  public @Nullable PsiElement getNameIdentifier() {
    return LuaDocPsiImplUtilKt.getNameIdentifier(this);
  }

  @Override
  public @NotNull ITy guessType(@NotNull SearchContext context) {
    return LuaDocPsiImplUtilKt.guessType(this, context);
  }

  @Override
  public boolean isDeprecated() {
    return LuaDocPsiImplUtilKt.isDeprecated(this);
  }

}
