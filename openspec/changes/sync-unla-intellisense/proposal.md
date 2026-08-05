## Why

UnLangServer 侧触发 UnLua Generate IntelliSense 后产出的注解位于 UE 工程内 `Plugins/Core/UnLua/Intermediate/IntelliSense/Script`。当前 `LuaUnLuaIntelliSenseProvider` 直接挂载该真实目录，依赖 IDE 能探测到 UE 工程路径，且引擎未运行/目录未生成时无法挂载。需要一条独立推送通道，让 UnLangServer 主动把生成结果同步到 IDE 私有缓存，脱离 UE 工程目录依赖，与蓝图注解通道解耦。

## What Changes

- 新增 HTTP 端点 `POST /api/unla-defs`（`RestService` ServiceName = `unla-defs`），接收 UnLangServer 推送的 UnLua IntelliSense 注解文件。
- 协议复用 `X-UE-Project` 广播自过滤 + `X-Def-Op`（upsert/delete/clear/begin-batch/end-batch）+ `X-Def-Path` + body，与 `ue-defs` 一致。
- 新增 `LuaUnLuaDefManager`（project service）：独立缓存目录 `lua-unla-defs/<locationHash>`，防抖 VFS 刷新，挂载状态跃迁触发 roots change。模式对齐 `LuaUEBlueprintManager`。
- 新增 `LuaUnLuaDefLibraryProvider`：把缓存目录以独立 SyntheticLibrary 挂入项目，标记预定义库文件。
- 保留现有 `LuaUnLuaIntelliSenseProvider`（仍挂载 UE 工程内真实 Script 目录）作为本地探测兜底；推送通道是补充而非替代。

## Capabilities

### New Capabilities
- `unla-defs-sync`: UnLangServer 经 HTTP 推送 UnLua IntelliSense 注解到 IDE 私有缓存目录并挂载为合成库。

### Modified Capabilities
<!-- 无现有 spec，留空 -->

## Impact

- 新增文件：`LuaUnLuaDefManager.kt`、`LuaUnLuaDefLibraryProvider.kt`、`LuaUnLuaDefHttpHandler.kt`。
- 注册：`plugin.xml` 声明新 `RestService` 与 `AdditionalLibraryRootsProvider`。
- 不改动现有 `LuaUnLuaIntelliSenseProvider` / `LuaUEBlueprintManager` / `LuaUEReflectManager`。
- UnLangServer 侧（非本仓库）需配合实现 Generate IntelliSense 后批量推送 Script 目录内容。
