/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 * Licensed under the Apache License, Version 2.0
 */
package com.tang.intellij.lua.editor.completion;

import com.intellij.codeInsight.lookup.LookupElementBuilder;

public class AttributeCompletionProvider extends LuaCompletionProvider {
    private final String[] names = {"const", "close"};

    @Override
    public void addCompletions(CompletionSession session) {
        for (String name : names) {
            session.addWord(name);
            session.getResultSet().addElement(LookupElementBuilder.create(name));
        }
        session.getResultSet().stopHere();
    }
}
