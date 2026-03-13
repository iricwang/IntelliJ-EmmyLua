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

import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.stubs.index.LuaClassIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class LuaClassNavigationContributor implements GotoClassContributor {

    @Override
    @Nullable
    public String getQualifiedName(@NotNull NavigationItem navigationItem) {
        return null;
    }

    @Override
    @Nullable
    public String getQualifiedNameSeparator() {
        return ".";
    }

    @Override
    public String @NotNull [] getNames(@NotNull Project project, boolean b) {
        Collection<String> allClasses = LuaClassIndex.instance.getAllKeys(project);
        return allClasses.toArray(new String[0]);
    }

    @Override
    public NavigationItem @NotNull [] getItemsByName(@NotNull String s, String s1, @NotNull Project project, boolean b) {
        NavigationItem classDef = LuaClassIndex.find(s, SearchContext.get(project));
        if (classDef == null) {
            return new NavigationItem[0];
        } else {
            return new NavigationItem[]{classDef};
        }
    }
}
