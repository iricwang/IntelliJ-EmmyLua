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

import com.intellij.codeInsight.generation.IndentedCommenter;
import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

public class LuaCommenter implements Commenter, IndentedCommenter {

    @Override
    @Nullable
    public String getLineCommentPrefix() {
        return "--";
    }

    @Override
    @Nullable
    public String getBlockCommentPrefix() {
        return "--[[";
    }

    @Override
    @Nullable
    public String getBlockCommentSuffix() {
        return "]]";
    }

    @Override
    @Nullable
    public String getCommentedBlockCommentPrefix() {
        return null;
    }

    @Override
    @Nullable
    public String getCommentedBlockCommentSuffix() {
        return null;
    }

    @Override
    @Nullable
    public Boolean forceIndentedLineComment() {
        return Boolean.TRUE;
    }
}
