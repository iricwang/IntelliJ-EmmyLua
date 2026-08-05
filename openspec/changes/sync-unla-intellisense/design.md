## Context

现有 UnLua IntelliSense 挂载（`LuaUnLuaIntelliSenseProvider`）直接读 UE 工程内 `Plugins/Core/UnLua/Intermediate/IntelliSense/Script` 真实目录。问题：

1. IDE 必须能探测到 UE 工程路径（`resolveUeProjectDir`），探测失败则不挂载。
2. 引擎未运行或未执行 Generate IntelliSense 时目录不存在，Provider 返回空。
3. 无主动通知机制，目录刚生成时 IDE 可能已启动且未触发重新挂载。

蓝图注解通道（`ue-defs`）已验证一套成熟模式：`RestService` + `X-UE-Project` 广播自过滤 + 独立缓存目录 + SyntheticLibrary + 防抖 VFS 刷新 + 挂载跃迁 roots change。本设计复用该模式，为 UnLua IntelliSense 建立独立推送通道。

## Goals / Non-Goals

**Goals:**
- UnLangServer 触发 UnLua Generate IntelliSense 后，经 `POST /api/unla-defs` 把 Script 目录内容推送到 IDE 私有缓存目录。
- IDE 侧独立缓存、独立挂载，脱离 UE 工程目录依赖。
- 协议与 `ue-defs` 对齐（同 op 集合、同 header 约定），降低 UnLangServer 侧实现成本。
- 支持批量推送（begin-batch/end-batch）与单文件 upsert/delete、全量 clear。

**Non-Goals:**
- 不替换现有 `LuaUnLuaIntelliSenseProvider`（仍作为本地探测兜底，处理引擎未推送但目录已存在的场景）。
- 不实现 UnLangServer 侧推送逻辑（非本仓库）。
- 不做增量 diff 协议（全量 clear + 批量 upsert 足够，注解文件量级百级）。
- 不做版本号/校验和协商（UnLangServer 负责何时全量重推）。

## Decisions

**D1: 独立端点而非复用 ue-defs**
- 复用 `ue-defs` 会让缓存目录混入 UnLua 注解，蓝图注解 clear 时误删。
- 独立 `unla-defs` 端点 + 独立 `LuaUnLuaDefManager` + 独立缓存目录 `lua-unla-defs/<locationHash>`，与蓝图注解完全解耦。
- 代价：多一个 RestService 注册、多一个 LibraryProvider，但代码可几乎照抄 `LuaUEBlueprintManager` / `LuaUEBlueprintLibraryProvider` / `LuaUEBlueprintHttpHandler`。

**D2: 复用 ue-defs 协议形态（header/op 约定）**
- `X-UE-Project` + `X-Def-Op`(upsert/delete/clear/begin-batch/end-batch) + `X-Def-Path` + body。
- UnLangServer 侧只需把 Generate IntelliSense 产出的 Script 目录遍历，begin-batch → 每个 .lua upsert → end-batch。
- 替代方案：打包 zip 一次推送。否决——增量更新（单个 WBP 重新生成）要全量重发，浪费；且 RestService body 走 Netty ByteBuf，大 zip 内存压力。

**D3: 缓存目录命名与隔离**
- `PathManager.getSystemPath()/lua-unla-defs/<project.locationHash>/`。
- 与 `lua-ue-defs`（蓝图）、`lua-reflect-defs`（反射）并列，互不干扰。
- 多开 IDE 按 locationHash 隔离，引擎广播自过滤到匹配工程。

**D4: 保留现有 LuaUnLuaIntelliSenseProvider**
- 推送通道是补充。引擎未推送但 Script 目录存在（手动 Generate 过）时，本地探测仍能挂载。
- 两个 Library 可能同时挂载同一逻辑内容的不同来源（一真一缓存），EmmyLua 注解解析按文件路径去重，不会重复定义（SyntheticLibrary 间不互斥）。
- 风险：同名字段在两处定义 → EmmyLua 取首个解析，行为与现有 std/xml defs 一致，可接受。

**D5: 防抖与刷新策略**
- 与 `LuaUEBlueprintManager` 一致：500ms 防抖、批量抑制、`VfsUtil.markDirtyAndRefresh`、挂载跃迁（空↔非空）时 EDT 触发 `makeRootsChange(RESCAN_DEPENDENCIES_IF_NEEDED)`。

## Risks / Trade-offs

- [两套 UnLua 注解来源并存可能冗余] → 缓存目录优先级低于真实目录？不强制，EmmyLua 解析去重即可。文档注明推送通道为主、本地探测为兜底。
- [UnLangServer 推送失败无感知] → 与 ue-defs 一致，HTTP 返回错误字符串，UnLangServer 侧自行重试/告警。IDE 不主动拉取。
- [缓存目录膨胀] → clear op 全量清理；UnLangServer 全量重同步前应先 clear。不做自动 GC。
- [RestService 注册冲突] → ServiceName `unla-defs` 需在 plugin.xml `<restApi.requestCreator>` 或平台注册表声明（参照 ue-defs 现有声明方式）。
