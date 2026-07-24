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

package com.tang.intellij.lua.xml

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.io.File

/**
 * 启动自动装配：
 * 1. 项目打开时若已配置 XML 定义目录，立即重建一次（内容未变则零开销，见 [LuaXmlManager.rebuild]）。
 * 2. 订阅 [LuaXmlSettings.TOPIC]：设置页变更目录后防抖重建。
 * 3. 订阅 VFS 变更：XML 定义目录下的 *.xml 增删改后防抖重建（IDE 外部编辑经自动刷新也会触发）。
 */
class LuaXmlStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val manager = LuaXmlManager.getInstance(project)
        manager.rebuild()

        val connection = project.messageBus.connect(manager)
        connection.subscribe(LuaXmlSettings.TOPIC, object : LuaXmlSettingsListener {
            override fun onChanged() = manager.scheduleRebuild()
        })
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val dir = LuaXmlSettings.getInstance(project).xmlDefDir.trim()
                if (dir.isEmpty()) return
                // VFileEvent.path 固定 '/' 分隔；目录选择器返回的亦是 '/'，此处归一化兜底手动输入。
                val prefix = File(dir).invariantSeparatorsPath.trimEnd('/') + "/"
                val hit = events.any {
                    it.path.startsWith(prefix) && it.path.endsWith(".xml", ignoreCase = true)
                }
                if (hit) manager.scheduleRebuild()
            }
        })
    }
}
