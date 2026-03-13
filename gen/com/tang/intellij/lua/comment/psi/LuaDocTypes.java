// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.tang.intellij.lua.lang.LuaParserDefinition;
import com.tang.intellij.lua.comment.psi.impl.*;

public interface LuaDocTypes {

  IElementType ACCESS_MODIFIER = LuaParserDefinition.createDocType("ACCESS_MODIFIER");
  IElementType ARR_TY = LuaParserDefinition.createDocType("ARR_TY");
  IElementType CLASS_NAME_REF = LuaParserDefinition.createDocType("CLASS_NAME_REF");
  IElementType COMMENT_STRING = LuaParserDefinition.createDocType("COMMENT_STRING");
  IElementType FUNCTION_PARAM = LuaParserDefinition.createDocType("FUNCTION_PARAM");
  IElementType FUNCTION_TY = LuaParserDefinition.createDocType("FUNCTION_TY");
  IElementType GENERAL_TY = LuaParserDefinition.createDocType("GENERAL_TY");
  IElementType GENERIC_DEF = LuaParserDefinition.createDocType("GENERIC_DEF");
  IElementType GENERIC_TY = LuaParserDefinition.createDocType("GENERIC_TY");
  IElementType LAN_ID = LuaParserDefinition.createDocType("LAN_ID");
  IElementType PARAM_NAME_REF = LuaParserDefinition.createDocType("PARAM_NAME_REF");
  IElementType PAR_TY = LuaParserDefinition.createDocType("PAR_TY");
  IElementType STRING_LITERAL_TY = LuaParserDefinition.createDocType("STRING_LITERAL_TY");
  IElementType TABLE_DEF = LuaParserDefinition.createDocType("TABLE_DEF");
  IElementType TABLE_FIELD = LuaParserDefinition.createDocType("TABLE_FIELD");
  IElementType TABLE_TY = LuaParserDefinition.createDocType("TABLE_TY");
  IElementType TAG_ALIAS = LuaParserDefinition.createDocType("TAG_ALIAS");
  IElementType TAG_CLASS = LuaParserDefinition.createDocType("TAG_CLASS");
  IElementType TAG_DEF = LuaParserDefinition.createDocType("TAG_DEF");
  IElementType TAG_FIELD = LuaParserDefinition.createDocType("TAG_FIELD");
  IElementType TAG_GENERIC_LIST = LuaParserDefinition.createDocType("TAG_GENERIC_LIST");
  IElementType TAG_LAN = LuaParserDefinition.createDocType("TAG_LAN");
  IElementType TAG_OVERLOAD = LuaParserDefinition.createDocType("TAG_OVERLOAD");
  IElementType TAG_PARAM = LuaParserDefinition.createDocType("TAG_PARAM");
  IElementType TAG_RETURN = LuaParserDefinition.createDocType("TAG_RETURN");
  IElementType TAG_SEE = LuaParserDefinition.createDocType("TAG_SEE");
  IElementType TAG_SUPPRESS = LuaParserDefinition.createDocType("TAG_SUPPRESS");
  IElementType TAG_TYPE = LuaParserDefinition.createDocType("TAG_TYPE");
  IElementType TAG_VARARG = LuaParserDefinition.createDocType("TAG_VARARG");
  IElementType TY = LuaParserDefinition.createDocType("TY");
  IElementType TYPE_LIST = LuaParserDefinition.createDocType("TYPE_LIST");
  IElementType UNION_TY = LuaParserDefinition.createDocType("UNION_TY");
  IElementType VARARG_PARAM = LuaParserDefinition.createDocType("VARARG_PARAM");

  IElementType ARR = new LuaDocTokenType("[]");
  IElementType AT = new LuaDocTokenType("@");
  IElementType COMMA = new LuaDocTokenType(",");
  IElementType DASHES = new LuaDocTokenType("DASHES");
  IElementType EQ = new LuaDocTokenType("=");
  IElementType EXTENDS = new LuaDocTokenType(":");
  IElementType FUN = new LuaDocTokenType("fun");
  IElementType GT = new LuaDocTokenType(">");
  IElementType ID = new LuaDocTokenType("ID");
  IElementType LCURLY = new LuaDocTokenType("{");
  IElementType LPAREN = new LuaDocTokenType("(");
  IElementType LT = new LuaDocTokenType("<");
  IElementType OR = new LuaDocTokenType("|");
  IElementType PRIVATE = new LuaDocTokenType("PRIVATE");
  IElementType PROTECTED = new LuaDocTokenType("PROTECTED");
  IElementType PUBLIC = new LuaDocTokenType("PUBLIC");
  IElementType RCURLY = new LuaDocTokenType("}");
  IElementType RPAREN = new LuaDocTokenType(")");
  IElementType SHARP = new LuaDocTokenType("#");
  IElementType STRING = new LuaDocTokenType("STRING");
  IElementType STRING_BEGIN = new LuaDocTokenType("STRING_BEGIN");
  IElementType STRING_LITERAL = new LuaDocTokenType("STRING_LITERAL");
  IElementType TAG_NAME = new LuaDocTokenType("TAG_NAME");
  IElementType TAG_NAME_ALIAS = new LuaDocTokenType("alias");
  IElementType TAG_NAME_CLASS = new LuaDocTokenType("class");
  IElementType TAG_NAME_FIELD = new LuaDocTokenType("field");
  IElementType TAG_NAME_GENERIC = new LuaDocTokenType("generic");
  IElementType TAG_NAME_LANGUAGE = new LuaDocTokenType("language");
  IElementType TAG_NAME_MODULE = new LuaDocTokenType("module");
  IElementType TAG_NAME_NAME = new LuaDocTokenType("TAG_NAME_NAME");
  IElementType TAG_NAME_OVERLOAD = new LuaDocTokenType("overload");
  IElementType TAG_NAME_PARAM = new LuaDocTokenType("param");
  IElementType TAG_NAME_PRIVATE = new LuaDocTokenType("private");
  IElementType TAG_NAME_PROTECTED = new LuaDocTokenType("protected");
  IElementType TAG_NAME_PUBLIC = new LuaDocTokenType("public");
  IElementType TAG_NAME_RETURN = new LuaDocTokenType("return");
  IElementType TAG_NAME_SEE = new LuaDocTokenType("see");
  IElementType TAG_NAME_SUPPRESS = new LuaDocTokenType("suppress");
  IElementType TAG_NAME_TYPE = new LuaDocTokenType("type");
  IElementType TAG_NAME_VARARG = new LuaDocTokenType("vararg");
  IElementType VARARG = new LuaDocTokenType("VARARG");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ACCESS_MODIFIER) {
        return new LuaDocAccessModifierImpl(node);
      }
      else if (type == ARR_TY) {
        return new LuaDocArrTyImpl(node);
      }
      else if (type == CLASS_NAME_REF) {
        return new LuaDocClassNameRefImpl(node);
      }
      else if (type == COMMENT_STRING) {
        return new LuaDocCommentStringImpl(node);
      }
      else if (type == FUNCTION_PARAM) {
        return new LuaDocFunctionParamImpl(node);
      }
      else if (type == FUNCTION_TY) {
        return new LuaDocFunctionTyImpl(node);
      }
      else if (type == GENERAL_TY) {
        return new LuaDocGeneralTyImpl(node);
      }
      else if (type == GENERIC_DEF) {
        return new LuaDocGenericDefImpl(node);
      }
      else if (type == GENERIC_TY) {
        return new LuaDocGenericTyImpl(node);
      }
      else if (type == LAN_ID) {
        return new LuaDocLanIdImpl(node);
      }
      else if (type == PARAM_NAME_REF) {
        return new LuaDocParamNameRefImpl(node);
      }
      else if (type == PAR_TY) {
        return new LuaDocParTyImpl(node);
      }
      else if (type == STRING_LITERAL_TY) {
        return new LuaDocStringLiteralTyImpl(node);
      }
      else if (type == TABLE_DEF) {
        return new LuaDocTableDefImpl(node);
      }
      else if (type == TABLE_FIELD) {
        return new LuaDocTableFieldImpl(node);
      }
      else if (type == TABLE_TY) {
        return new LuaDocTableTyImpl(node);
      }
      else if (type == TAG_ALIAS) {
        return new LuaDocTagAliasImpl(node);
      }
      else if (type == TAG_CLASS) {
        return new LuaDocTagClassImpl(node);
      }
      else if (type == TAG_DEF) {
        return new LuaDocTagDefImpl(node);
      }
      else if (type == TAG_FIELD) {
        return new LuaDocTagFieldImpl(node);
      }
      else if (type == TAG_GENERIC_LIST) {
        return new LuaDocTagGenericListImpl(node);
      }
      else if (type == TAG_LAN) {
        return new LuaDocTagLanImpl(node);
      }
      else if (type == TAG_OVERLOAD) {
        return new LuaDocTagOverloadImpl(node);
      }
      else if (type == TAG_PARAM) {
        return new LuaDocTagParamImpl(node);
      }
      else if (type == TAG_RETURN) {
        return new LuaDocTagReturnImpl(node);
      }
      else if (type == TAG_SEE) {
        return new LuaDocTagSeeImpl(node);
      }
      else if (type == TAG_SUPPRESS) {
        return new LuaDocTagSuppressImpl(node);
      }
      else if (type == TAG_TYPE) {
        return new LuaDocTagTypeImpl(node);
      }
      else if (type == TAG_VARARG) {
        return new LuaDocTagVarargImpl(node);
      }
      else if (type == TYPE_LIST) {
        return new LuaDocTypeListImpl(node);
      }
      else if (type == UNION_TY) {
        return new LuaDocUnionTyImpl(node);
      }
      else if (type == VARARG_PARAM) {
        return new LuaDocVarargParamImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
