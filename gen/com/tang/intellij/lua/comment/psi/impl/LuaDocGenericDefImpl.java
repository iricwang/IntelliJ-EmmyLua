// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.tang.intellij.lua.comment.psi.LuaDocTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.tang.intellij.lua.comment.psi.*;

public class LuaDocGenericDefImpl extends ASTWrapperPsiElement implements LuaDocGenericDef {

  public LuaDocGenericDefImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull LuaDocVisitor visitor) {
    visitor.visitGenericDef(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaDocVisitor) accept((LuaDocVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public LuaDocClassNameRef getClassNameRef() {
    return PsiTreeUtil.getChildOfType(this, LuaDocClassNameRef.class);
  }

  @Override
  @NotNull
  public PsiElement getId() {
    return notNullChild(findChildByType(ID));
  }

  @Override
  public @Nullable PsiElement getNameIdentifier() {
    return LuaDocPsiImplUtilKt.getNameIdentifier(this);
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
  public int getTextOffset() {
    return LuaDocPsiImplUtilKt.getTextOffset(this);
  }

}
