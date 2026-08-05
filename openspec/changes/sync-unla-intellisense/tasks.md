## 1. Manager 核心

- [x] 1.1 新建 `LuaUnLuaDefManager.kt`（project service, Disposable）：照抄 `LuaUEBlueprintManager` 骨架，缓存根名改为 `lua-unla-defs`，防抖 500ms，batching 抑制，`initMounted`/`refreshNow`/挂载跃迁 roots change。
- [x] 1.2 实现 `upsertDef(relPath, content)` / `deleteDef(relPath)` / `clearDefs()` / `beginBatch()` / `endBatch()`，路径消毒（拒绝对路径与 `..` 逃逸）复用 `LuaUEBlueprintManager.resolveDefFile` 同款逻辑。
- [x] 1.3 复用 `LuaUEBlueprintManager.matches` 的工程匹配能力（直接调 `LuaUEBlueprintManager.getInstance(project).matches(ueProject)` 或抽公共工具，避免重复实现）。

## 2. HTTP 接收端

- [x] 2.1 新建 `LuaUnLuaDefHttpHandler.kt`（extends `RestService`），`getServiceName()` 返回 `unla-defs`。
- [x] 2.2 `execute` 照抄 `LuaUEBlueprintHttpHandler`：取 `X-UE-Project` → `ProjectManager.openProjects` 找匹配 `LuaUnLuaDefManager` → 按 `X-Def-Op` 分派 upsert/delete/clear/begin-batch/end-batch，返回 null=OK / 字符串=错误。

## 3. Library 挂载

- [x] 3.1 新建 `LuaUnLuaDefLibraryProvider.kt`（extends `AdditionalLibraryRootsProvider`）：照抄 `LuaUEBlueprintLibraryProvider`，`getAdditionalProjectLibraries` 调 `LuaUnLuaDefManager.getCacheRoot()`，子文件标记 `LuaFileUtil.PREDEFINED_KEY`。
- [x] 3.2 内部 `LuaUnLuaDefLibrary`（SyntheticLibrary + ItemPresentation），presentableText = `UnLua IntelliSense (synced)`，locationString = `UnLua defs (pushed)`，icon = `LuaIcons.FILE`。

## 4. plugin.xml 注册

- [x] 4.1 查阅 `plugin.xml` 现有 `ue-defs` RestService 与 `LuaUEBlueprintLibraryProvider` 声明方式。
- [x] 4.2 按同样方式注册 `LuaUnLuaDefHttpHandler`（RestService）与 `LuaUnLuaDefLibraryProvider`（additionalLibraryRootsProvider / project-level component）。
- [x] 4.3 若 RestService 需在 `com.intellij.ide.restService` 扩展点声明，按 `ue-defs` 现有声明照抄一份改名为 `unla-defs`。

## 5. 启动装配

- [x] 5.1 在 `LuaUEBlueprintStartupActivity.execute` 末尾调 `LuaUnLuaDefManager.getInstance(project).initMounted()` + `refreshNow()`（与蓝图 manager 同步初始化，把引擎在 IDE 关闭期间推送落盘的改动刷进索引）。

## 6. 验证

- [x] 6.1 编译通过（`./gradlew compileKotlin`）。
- [x] 6.2 手动 curl 模拟推送：`begin-batch` → upsert 几个 .lua → `end-batch`，确认缓存目录有文件、Library 挂载、EmmyLua 能解析 `---@class UWindow`。
- [x] 6.3 测试 clear、delete、工程不匹配、缺头错误返回。
- [x] 6.4 测试多开 IDE 隔离（locationHash 不同目录）。

## 7. 修复 POST 不被支持（验证时发现）

- [x] 7.1 `LuaUnLuaDefHttpHandler` override `isMethodSupported` 返回 `POST`（RestService 默认只支持 GET，导致 POST 请求 404）。
- [x] 7.2 顺手修 `LuaUEBlueprintHttpHandler` 同样问题（预先存在的 bug）。
