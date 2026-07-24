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

package com.tang.intellij.test.completion

import com.tang.intellij.lua.ue.LuaUEBlueprintHttpHandler
import com.tang.intellij.lua.ue.LuaUEBlueprintManager
import com.tang.intellij.lua.ue.LuaUEBlueprintSettings
import com.tang.intellij.test.LuaTestBase
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import java.io.File

/**
 * UE 蓝图注解接收端（[LuaUEBlueprintHttpHandler]）的请求解析与分发逻辑测试。
 * 直接构造 netty 请求调用 execute（不经内建服务器），覆盖 header 解析、工程匹配、
 * upsert/delete/clear 与路径消毒。
 */
class TestLuaUEBlueprintHandler : LuaTestBase() {

    private val handler = LuaUEBlueprintHttpHandler()

    private fun post(
        ueProject: String?,
        op: String? = null,
        path: String? = null,
        body: String = "",
    ): String? {
        val request = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/ue-defs",
            Unpooled.copiedBuffer(body.toByteArray(Charsets.UTF_8))
        )
        ueProject?.let { request.headers().set("X-UE-Project", it) }
        op?.let { request.headers().set("X-Def-Op", it) }
        path?.let { request.headers().set("X-Def-Path", it) }
        // execute 实现不使用 urlDecoder/context，传占位对象即可
        val ctx = java.lang.reflect.Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(io.netty.channel.ChannelHandlerContext::class.java)
        ) { _, _, _ -> null } as io.netty.channel.ChannelHandlerContext
        return handler.execute(io.netty.handler.codec.http.QueryStringDecoder("/api/ue-defs"), request, ctx)
    }

    override fun setUp() {
        super.setUp()
        // 强制匹配：把 UE 工程目录覆盖为固定值
        LuaUEBlueprintSettings.getInstance(project).ueProjectDir = "X:/FakeUE"
        LuaUEBlueprintManager.getInstance(project).clearDefs()
    }

    fun testMissingUeProjectHeader() {
        val err = post(ueProject = null)
        assertEquals("missing X-UE-Project header", err)
    }

    fun testNoMatchingProject() {
        val err = post(ueProject = "Z:/OtherUE/Project")
        assertTrue("应返回不匹配错误，实际：$err", err!!.startsWith("no matching project"))
    }

    fun testUpsertAndDelete() {
        val manager = LuaUEBlueprintManager.getInstance(project)
        val content = "---@class WBP_Test_C\nlocal WBP_Test_C = {}\n"
        val err = post("X:/FakeUE", path = "GamePlay/WBP_Test.lua", body = content)
        assertNull("upsert 应成功，实际：$err", err)

        val defFile = File(manager.getCacheDir(), "GamePlay/WBP_Test.lua")
        assertTrue("注解文件应写入缓存目录", defFile.isFile)
        assertEquals(content, defFile.readText(Charsets.UTF_8))

        assertNull(post("X:/FakeUE", op = "delete", path = "GamePlay/WBP_Test.lua"))
        assertFalse("delete 后文件应不存在", defFile.exists())
    }

    fun testClear() {
        val manager = LuaUEBlueprintManager.getInstance(project)
        post("X:/FakeUE", path = "A.lua", body = "---@class A\n")
        post("X:/FakeUE", path = "sub/B.lua", body = "---@class B\n")
        assertNull(post("X:/FakeUE", op = "clear"))
        assertTrue("clear 后缓存目录应为空", manager.getCacheDir().list()?.isEmpty() != false)
    }

    fun testPathTraversalRejected() {
        val err = post("X:/FakeUE", path = "../evil.lua", body = "x")
        assertEquals("invalid X-Def-Path: ../evil.lua", err)
    }

    fun testBatchOps() {
        assertNull(post("X:/FakeUE", op = "begin-batch"))
        assertNull(post("X:/FakeUE", path = "A.lua", body = "---@class A\n"))
        assertNull(post("X:/FakeUE", op = "end-batch"))
    }
}
