## ADDED Requirements

### Requirement: 接收 UnLua IntelliSense 注解推送
IDE SHALL 提供 HTTP 端点 `POST /api/unla-defs`（RestService ServiceName = `unla-defs`），接收 UnLangServer 推送的 UnLua IntelliSense 注解文件。协议 MUST 与 `ue-defs` 一致：`X-UE-Project` 头做广播自过滤，`X-Def-Op` 取值 `upsert`（默认）| `delete` | `clear` | `begin-batch` | `end-batch`，`X-Def-Path` 为相对路径，body 为 UTF-8 注解文本。

#### Scenario: 单文件 upsert
- **WHEN** 引擎推送 `POST /api/unla-defs`，`X-Def-Op=upsert`，`X-Def-Path=Gameplay/UWindow.lua`，body 为注解文本
- **THEN** IDE 把内容写入缓存目录 `lua-unla-defs/<locationHash>/Gameplay/UWindow.lua`，返回 200 OK

#### Scenario: 批量推送
- **WHEN** 引擎先 `begin-batch`，连续 upsert 多个文件，最后 `end-batch`
- **THEN** IDE 在 begin-batch 期间抑制 VFS 刷新，end-batch 时合并为一次刷新

#### Scenario: 删除单文件
- **WHEN** 引擎推送 `X-Def-Op=delete`，`X-Def-Path=Gameplay/UWindow.lua`
- **THEN** IDE 删除缓存目录下对应文件并触发刷新

#### Scenario: 全量清空
- **WHEN** 引擎推送 `X-Def-Op=clear`
- **THEN** IDE 清空缓存目录下全部内容并触发刷新

#### Scenario: 工程不匹配
- **WHEN** `X-UE-Project` 不匹配任何打开工程的 UE 工程目录
- **THEN** 返回错误字符串 `no matching project for: <ueProject>`，不写盘

#### Scenario: 缺少必要头
- **WHEN** upsert/delete 请求缺少 `X-Def-Path` 或缺少 `X-UE-Project`
- **THEN** 返回错误描述字符串，不写盘

### Requirement: 独立缓存目录隔离
IDE SHALL 把 UnLua IntelliSense 推送内容存入独立缓存目录 `PathManager.getSystemPath()/lua-unla-defs/<project.locationHash>/`，与蓝图注解缓存（`lua-ue-defs`）和反射注解缓存（`lua-reflect-defs`）完全隔离。

#### Scenario: 与蓝图注解解耦
- **WHEN** 蓝图注解通道执行 `clear`
- **THEN** UnLua IntelliSense 缓存目录内容不受影响

#### Scenario: 多开 IDE 隔离
- **WHEN** 两个 IDE 实例打开不同工程
- **THEN** 各自缓存目录按 `project.locationHash` 隔离，互不干扰

### Requirement: 合成库挂载
IDE SHALL 通过 `AdditionalLibraryRootsProvider` 把 UnLua IntelliSense 缓存目录以独立 SyntheticLibrary 挂入项目，缓存目录下文件标记为预定义库文件（`LuaFileUtil.PREDEFINED_KEY`）。挂载条件：缓存目录存在且非空。

#### Scenario: 首次推送后挂载
- **WHEN** 缓存目录从空变为非空（首次 upsert 成功）
- **THEN** SyntheticLibrary 挂载该目录，平台触发 roots change 重建索引

#### Scenario: 全部删除后卸载
- **WHEN** 缓存目录从非空变为空（clear 或全部 delete）
- **THEN** SyntheticLibrary 不再挂载该目录，平台触发 roots change

### Requirement: 防抖刷新
IDE SHALL 对 VFS 刷新做 500ms 防抖：连续推送只在最后一次请求后 500ms 触发一次 `VfsUtil.markDirtyAndRefresh`。批量期间（begin-batch 与 end-batch 之间）MUST 抑制刷新。

#### Scenario: 连续单文件推送合并刷新
- **WHEN** 100ms 内连续 upsert 5 个文件
- **THEN** 只在最后一次 upsert 后 500ms 触发一次 VFS 刷新

#### Scenario: 批量期间不刷新
- **WHEN** 处于 begin-batch 与 end-batch 之间，upsert 多个文件
- **THEN** 不触发 VFS 刷新，end-batch 时触发一次

### Requirement: 保留本地探测兜底
IDE MUST 保留现有 `LuaUnLuaIntelliSenseProvider`（挂载 UE 工程内真实 `Plugins/Core/UnLua/Intermediate/IntelliSense/Script` 目录）。推送通道是补充，

#### Scenario: 引擎未推送但目录存在
- **WHEN** 引擎从未推送，但 UE 工程内 Script 目录已存在（手动 Generate 过）
- **THEN** `LuaUnLuaIntelliSenseProvider` 仍挂载该真实目录

#### Scenario: 两来源并存
- **WHEN** 推送缓存目录与真实 Script 目录同时存在
- **THEN** 两个 SyntheticLibrary 均挂载，EmmyLua 注解解析按文件路径去重
