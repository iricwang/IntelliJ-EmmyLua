/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 * Licensed under the Apache License, Version 2.0
 */
package com.tang.intellij.lua.editor.completion;

import com.tang.intellij.lua.psi.LuaFuncBodyOwner;
import com.tang.intellij.lua.psi.LuaParamInfo;

public class FuncInsertHandler extends ArgsInsertHandler {
    private final LuaFuncBodyOwner funcBodyOwner;

    public FuncInsertHandler(LuaFuncBodyOwner funcBodyOwner) {
        this.funcBodyOwner = funcBodyOwner;
    }

    @Override
    public LuaParamInfo[] getParams() {
        return funcBodyOwner.getParams();
    }

    @Override
    public boolean isVarargs() {
        return funcBodyOwner.getFuncBody() != null && funcBodyOwner.getFuncBody().getEllipsis() != null;
    }
}
