// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.psi.LuaTypes.*;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.tang.intellij.lua.stubs.LuaTableFieldStub;
import com.tang.intellij.lua.psi.*;
import com.intellij.navigation.ItemPresentation;
import com.tang.intellij.lua.comment.psi.api.LuaComment;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class LuaTableFieldImpl extends StubBasedPsiElementBase<LuaTableFieldStub> implements LuaTableField {

  public LuaTableFieldImpl(@NotNull LuaTableFieldStub stub, @NotNull IStubElementType type) {
    super(stub, type);
  }

  public LuaTableFieldImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaTableFieldImpl(LuaTableFieldStub stub, IElementType type, ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitTableField(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<LuaExpr> getExprList() {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(this, LuaExpr.class);
  }

  @Override
  @Nullable
  public PsiElement getId() {
    return findChildByType(ID);
  }

  @Override
  @Nullable
  public PsiElement getNameIdentifier() {
    return LuaPsiImplUtil.getNameIdentifier(this);
  }

  @Override
  @NotNull
  public PsiElement setName(@NotNull String name) {
    return LuaPsiImplUtil.setName(this, name);
  }

  @Override
  @Nullable
  public String getName() {
    return LuaPsiImplUtil.getName(this);
  }

  @Override
  public int getTextOffset() {
    return LuaPsiImplUtil.getTextOffset(this);
  }

  @Override
  @NotNull
  public String toString() {
    return LuaPsiImplUtil.toString(this);
  }

  @Override
  @Nullable
  public String getFieldName() {
    return LuaPsiImplUtil.getFieldName(this);
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return LuaPsiImplUtil.getPresentation(this);
  }

  @Override
  @NotNull
  public ITy guessParentType(@NotNull SearchContext context) {
    return LuaPsiImplUtil.guessParentType(this, context);
  }

  @Override
  @NotNull
  public Visibility getVisibility() {
    return LuaPsiImplUtil.getVisibility(this);
  }

  @Override
  public int getWorth() {
    return LuaPsiImplUtil.getWorth(this);
  }

  @Override
  public boolean isDeprecated() {
    return LuaPsiImplUtil.isDeprecated(this);
  }

  @Override
  @Nullable
  public LuaComment getComment() {
    return LuaPsiImplUtil.getComment(this);
  }

  @Override
  @Nullable
  public LuaExpr getIdExpr() {
    return LuaPsiImplUtil.getIdExpr(this);
  }

  @Override
  @Nullable
  public PsiElement getLbrack() {
    return findChildByType(LBRACK);
  }

}
