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

package com.tang.intellij.lua.debugger

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfoRt
import java.io.File

/**
 * 调试器运行库（emmy_core 原生库 / emmyHelper.lua / mobdebug.lua）部署器。
 *
 * 这些文件原本直接引用插件安装目录（开发环境下会落到 build/ 沙箱目录，易失且不美观），
 * 改为首次使用时释放到固定用户目录 `~/.emmylua/debugger`，并按插件版本号自动更新。
 * 资源经插件 classloader 读取，插件以 jar 或目录形式安装都能工作。
 */
object LuaDebuggerLibs {

    private val LOG = Logger.getInstance(LuaDebuggerLibs::class.java)

    /** 用户目录下的固定部署根 */
    val rootDir: File by lazy { File(System.getProperty("user.home"), ".emmylua/debugger") }

    /** 需要释放的插件资源（相对 classpath 根） */
    private val RESOURCES = listOf(
        "debugger/emmy/emmyHelper.lua",
        "debugger/emmy/windows/x64/emmy_core.dll",
        "debugger/emmy/windows/x86/emmy_core.dll",
        "debugger/emmy/mac/arm64/emmy_core.dylib",
        "debugger/emmy/mac/x64/emmy_core.dylib",
        "debugger/emmy/linux/emmy_core.so",
        "debugger/mobdebug/mobdebug.lua",
    )

    private fun pluginVersion(): String {
        val descriptor = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(LuaDebuggerLibs::class.java.name)
        return descriptor?.version ?: "unknown"
    }

    /** 确保运行库已释放且与当前插件版本一致，返回部署根目录 */
    @Synchronized
    fun ensureDeployed(): File {
        val marker = File(rootDir, ".version")
        val version = pluginVersion()
        //除版本标记外还要校验文件真实存在：
        //早期构建可能部署失败但已写入标记，不能仅凭标记跳过
        val upToDate = try {
            marker.isFile && marker.readText().trim() == version &&
                File(rootDir, "emmy/emmyHelper.lua").isFile
        } catch (e: Exception) {
            false
        }
        if (!upToDate) {
            val deployedCount = deployAll()
            //确有文件落地才写标记，避免失败部署阻塞下次重试
            if (deployedCount > 0) {
                try {
                    marker.parentFile?.mkdirs()
                    marker.writeText(version)
                } catch (e: Exception) {
                    LOG.warn("Failed to write debugger libs version marker", e)
                }
            }
        }
        return rootDir
    }

    private fun deployAll(): Int {
        //std/debugger 资源不打进 jar，而是在插件根目录（见 build.gradle.kts 的 PrepareSandboxTask），
        //因此优先按插件目录的磁盘路径读取；classloader 兜底开发环境（build/resources/main 在 classpath）
        val pluginDir = PluginManagerCore
            .getPluginDescriptorOrPlatformByClassName(LuaDebuggerLibs::class.java.name)
            ?.pluginPath?.toFile()
        val classLoader = LuaDebuggerLibs::class.java.classLoader
        var deployedCount = 0
        for (res in RESOURCES) {
            try {
                val target = File(rootDir, res.removePrefix("debugger/"))
                target.parentFile?.mkdirs()
                val srcFile = pluginDir?.let { File(it, res) }
                val deployed = when {
                    srcFile != null && srcFile.isFile -> {
                        srcFile.inputStream().use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        true
                    }
                    else -> classLoader.getResourceAsStream(res)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } != null
                }
                if (!deployed) {
                    LOG.warn("Debugger resource not found: $res (pluginDir=$pluginDir)")
                    continue
                }
                if (!SystemInfoRt.isWindows)
                    target.setExecutable(true)
                deployedCount++
            } catch (e: Exception) {
                //目标 dll 可能正被调试中的进程占用，保留旧文件继续用
                LOG.warn("Failed to deploy debugger resource: $res", e)
            }
        }
        return deployedCount
    }

    /** emmy 运行库根目录（含 emmyHelper.lua 与各平台子目录） */
    fun getEmmyDir(): File = File(ensureDeployed(), "emmy")

    /** 当前平台的 emmy_core 所在目录（windows/mac/linux） */
    fun getEmmyPlatformDir(): File {
        val platform = when {
            SystemInfoRt.isWindows -> "windows"
            SystemInfoRt.isMac -> "mac"
            else -> "linux"
        }
        return File(getEmmyDir(), platform)
    }

    /** emmyHelper.lua（调试会话初始化时发送给被调试进程） */
    fun getEmmyHelperFile(): File = File(getEmmyDir(), "emmyHelper.lua")

    /** mobdebug.lua 所在目录（用于 package.path） */
    fun getMobdebugDir(): File = File(ensureDeployed(), "mobdebug")
}
