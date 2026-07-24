# UE 蓝图注解同步协议（引擎 UnLangService ↔ IDEA 插件）

## 发现（IDE 注册表）

每个在线 IDEA 实例的每个工程，在启动时写入一个注册表文件：

- 目录：`%LOCALAPPDATA%\EmmyLuaDefs\ide-registry\`（非 Windows 为 `~/.cache/EmmyLuaDefs/ide-registry/`）
- 文件名：`<pid>-<projectHash>.json`，工程关闭/IDE 退出时删除
- 内容：

```json
{
  "pid": 12345,
  "port": 63342,
  "ide": "idea",
  "projectBasePath": "X:/L46_workspace/L46_workspace_trunk/FortySix/Source/LuaScripts",
  "ueProjectDir": "X:/L46_workspace/L46_workspace_trunk/FortySix",
  "startedAt": 1784787788980
}
```

引擎侧发送前枚举该目录所有 `*.json`，**向每个 port 广播**；IDE 端自行按
`X-UE-Project` 过滤匹配，不匹配的请求会被忽略（返回错误描述 `"no matching project"`，
可静默忽略）。陈旧注册表（PID 已死）连接失败时忽略即可，也可顺带清理。

## 请求

`POST http://127.0.0.1:<port>/api/ue-defs`

Headers：

| Header | 必填 | 说明 |
|---|---|---|
| `X-UE-Project` | 是 | UE 工程根目录（含 .uproject 的目录），正斜杠或反斜杠均可 |
| `X-Def-Op` | 否 | `upsert`（默认）/ `delete` / `clear` / `begin-batch` / `end-batch` |
| `X-Def-Path` | upsert/delete 必填 | 相对路径，如 `GamePlay/WBP_BagItem.lua`；不允许 `..` |

Body：`upsert` 时为注解文本（UTF-8），其余操作忽略。

语义：

- `upsert`：写入/覆盖单个 WBP 注解文件（IDE 防抖 ~500ms 后刷新索引）
- `delete`：删除单个文件（WBP 删除/重命名时用；重命名 = delete 旧 + upsert 新）
- `clear`：清空本工程全部 UE 注解（全量重同步前调用）
- `begin-batch` / `end-batch`：批量推送包裹，`end-batch` 后合并为一次索引刷新。
  一次编译几百个 WBP 时务必使用，否则每个文件触发一次防抖调度（虽有去重，仍有浪费）

响应：200 表示成功；非 200 或错误文本见 body（`no matching project` 可忽略）。

## 示例（curl 联调）

```bash
# upsert
curl -X POST "http://127.0.0.1:63342/api/ue-defs" ^
  -H "X-UE-Project: X:/L46_workspace/L46_workspace_trunk/FortySix" ^
  -H "X-Def-Op: upsert" ^
  -H "X-Def-Path: GamePlay/WBP_Test.lua" ^
  -H "Content-Type: application/octet-stream" ^
  --data-binary "@WBP_Test.lua"

# batch
curl -X POST "http://127.0.0.1:63342/api/ue-defs" -H "X-UE-Project: X:/L46_workspace/L46_workspace_trunk/FortySix" -H "X-Def-Op: begin-batch"
... N 个 upsert ...
curl -X POST "http://127.0.0.1:63342/api/ue-defs" -H "X-UE-Project: X:/L46_workspace/L46_workspace_trunk/FortySix" -H "X-Def-Op: end-batch"
```

## 引擎侧改造要点（FEmmyLuaClient）

1. 去掉硬编码 `127.0.0.1:996`，改为枚举注册表目录广播
2. WBP 编译完成事件 → `IntelliSense::Get(Blueprint)` 产文本 → upsert
3. 资产删除/重命名事件 → delete（重命名补一条 upsert 新路径）
4. 批量编译/全量导出 → begin-batch → N×upsert → end-batch；全量重同步先 clear
5. 发送全程在异步线程（现有 `Async(EAsyncExecution::Thread, ...)` 模式即可）
