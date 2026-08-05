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

package com.tang.intellij.lua.codeInsight.inspection

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.tang.intellij.lua.lang.LuaFileType
import com.tang.intellij.lua.psi.LuaIndexExpr
import com.tang.intellij.lua.psi.LuaPsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color

/**
 * 「组件不存在」错误的编辑器 tab 显眼标记 + Project 树/Problems 窗口文件级报红。
 *
 * tab 标记：标题加 ❌ 前缀 + 底色标红。文件级报红：经
 * [WolfTheProblemSolver.reportProblemsFromExternalSource] 上报（外部问题源通道，
 * 与 daemon 自带上报互不干扰——inspection 的 ProblemHighlightType.ERROR 只影响编辑器内
 * 波浪线样式，不进 Wolf，Project 树不会自动红）。
 *
 * 跟踪方式：订阅 daemon 完成事件 → 防抖重查所有打开中的 Lua 文件
 * （复用 [UeWidgetMemberInspection.unknownMemberMessage] 的判定）→ 状态变化时刷 tab 与 Wolf。
 *
 * 注意 [EditorTabColorProvider]/[EditorTabTitleProvider] 只在 insertTab（打开 tab）时被询问一次，
 * 错误的动态出现/消失（编辑代码、引擎重推注解）必须直接改 TabInfo（[updateTab]），
 * 两个 Provider 只负责新打开 tab 的即时标记。
 */
@Service(Service.Level.PROJECT)
class ViewModelWidgetProblemService(private val project: Project, private val scope: CoroutineScope) : Disposable {

    companion object {
        const val TAB_PREFIX = "❌ "

        /** tab 底色：浅红/暗红，尽量显眼。 */
        val TAB_COLOR: Color get() = JBColor(Color(0xFF, 0xC9, 0xC9), Color(0x7A, 0x2D, 0x2D))

        /** Wolf 外部问题源标识（report/clear 需传同一对象）。 */
        private val WOLF_SOURCE = Any()

        fun getInstance(project: Project): ViewModelWidgetProblemService =
            project.getService(ViewModelWidgetProblemService::class.java)
    }

    /** 当前含「组件不存在」错误的打开中文件。 */
    @Volatile
    private var problematicFiles: Set<VirtualFile> = emptySet()

    private var recheckJob: Job? = null

    init {
        val conn = project.messageBus.connect(this)
        conn.subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonFinished() = scheduleRecheck()
                override fun daemonFinished(fileEditors: Collection<FileEditor>) = scheduleRecheck()
            }
        )
        // tab 关闭时清掉 Wolf 外部源标记，避免 Project 树残留报红（tab 标记随 tab 消失自动没了）
        conn.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    if (problematicFiles.contains(file)) {
                        problematicFiles = problematicFiles - file
                        // fileClosed 在 EDT 触发，Wolf 必须非 EDT，丢到后台
                        scope.launch {
                            WolfTheProblemSolver.getInstance(project)
                                .clearProblemsFromExternalSource(file, WOLF_SOURCE)
                        }
                    }
                }
            }
        )
    }

    fun hasProblem(file: VirtualFile): Boolean = problematicFiles.contains(file)

    private fun scheduleRecheck() {
        recheckJob?.cancel()
        recheckJob = scope.launch {
            delay(400)
            recheck()
        }
    }

    private suspend fun recheck() {
        val openLuaFiles = FileEditorManager.getInstance(project).openFiles
            .filter { it.fileType == LuaFileType.INSTANCE }
        val newSet = readAction {
            openLuaFiles.filterTo(hashSetOf()) { hasUnknownWidget(it) }
        }
        val oldSet = problematicFiles
        if (newSet == oldSet) return
        problematicFiles = newSet
        // Wolf 上报必须非 EDT（reportProblemsFromExternalSource 内部 assertIsNonDispatchThread，
        // 沙箱 IDE 开断言会直接抛 AssertionError）——放在后台协程上下文里调用。
        val wolf = WolfTheProblemSolver.getInstance(project)
        (newSet - oldSet).forEach { wolf.reportProblemsFromExternalSource(it, WOLF_SOURCE) }
        (oldSet - newSet).forEach { wolf.clearProblemsFromExternalSource(it, WOLF_SOURCE) }
        val changed = newSet union oldSet
        withContext(Dispatchers.EDT) {
            changed.forEach { updateTab(it, it in newSet) }
        }
    }

    /** 调用处需持有读锁。 */
    private fun hasUnknownWidget(file: VirtualFile): Boolean {
        if (DumbService.isDumb(project)) return false
        val psi = PsiManager.getInstance(project).findFile(file) as? LuaPsiFile ?: return false
        return PsiTreeUtil.findChildrenOfType(psi, LuaIndexExpr::class.java)
            .any { UeWidgetMemberInspection.unknownMemberMessage(it) != null }
    }

    /** 直接改 TabInfo（Provider 只在 tab 创建时被问一次，动态变化必须自己刷）。 */
    private fun updateTab(file: VirtualFile, problematic: Boolean) {
        if (project.isDisposed) return
        for (window in FileEditorManagerEx.getInstanceEx(project).windows) {
            val info = window.tabbedPane.tabs.findInfo(file) ?: continue
            if (problematic) {
                if (!info.text.startsWith(TAB_PREFIX)) info.setText(TAB_PREFIX + info.text)
                info.setTabColor(TAB_COLOR)
            } else {
                if (info.text.startsWith(TAB_PREFIX)) {
                    info.setText(EditorTabPresentationUtil.getEditorTabTitle(project, file))
                }
                info.setTabColor(null)
            }
        }
    }

    override fun dispose() {}
}

/** 新打开 tab 时的即时底色标记（动态变化由 [ViewModelWidgetProblemService.updateTab] 负责）。 */
class ViewModelWidgetTabColorProvider : EditorTabColorProvider {
    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? =
        if (ViewModelWidgetProblemService.getInstance(project).hasProblem(file))
            ViewModelWidgetProblemService.TAB_COLOR
        else null
}

/** 新打开 tab 时的即时标题前缀（动态变化由 [ViewModelWidgetProblemService.updateTab] 负责）。 */
class ViewModelWidgetTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? =
        if (ViewModelWidgetProblemService.getInstance(project).hasProblem(file))
            ViewModelWidgetProblemService.TAB_PREFIX + EditorTabPresentationUtil.getEditorTabTitle(project, file)
        else null
}
