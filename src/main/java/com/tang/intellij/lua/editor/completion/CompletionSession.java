/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 * Licensed under the Apache License, Version 2.0
 */
package com.tang.intellij.lua.editor.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class CompletionSession {
    public static final Key<CompletionSession> KEY = Key.create("lua.CompletionSession");

    private final CompletionParameters parameters;
    private final CompletionResultSet resultSet;
    public boolean isSuggestWords = true;
    private final HashSet<String> words = new HashSet<>();

    public CompletionSession(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet resultSet) {
        this.parameters = parameters;
        this.resultSet = resultSet;
    }

    public CompletionParameters getParameters() { return parameters; }
    public CompletionResultSet getResultSet() { return resultSet; }

    public boolean addWord(String word) { return words.add(word); }

    @NotNull
    public static CompletionSession get(@NotNull CompletionParameters completionParameters) {
        return completionParameters.getEditor().getUserData(KEY);
    }
}
