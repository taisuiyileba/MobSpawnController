# 自然生成界面优化方案

## 背景与目标

当前自然生成编辑界面中，方块 ID 名单（脚下方块、生成处方块、上方方块）使用与维度/生物群系/结构相同的多选列表子界面，玩家必须从海量方块中搜索勾选，不够直观。用户希望改为**每行一个方块 ID 的手写列表**，支持增删行、保存时验证 ID 正确性、支持 `#tag` 标签。

同时，天气/难度等 CYCLE 控件的左右箭头风格与整体面板不够统一；用户也希望借此机会优化其他自然生成界面的交互逻辑，并在完成后获得一份可补充的生成条件清单。

## 范围

本次实施范围：**仅做界面与客户端交互改造，不新增 NaturalSpawnSettings 字段**。新增生成条件将在界面改造完成后以清单形式提供给用户，由用户决定是否实施。

## 关键文件

- `common/src/main/java/com/mobspawncontroller/client/gui/MobSpawnEditScreen.java`
- `common/src/main/java/com/mobspawncontroller/client/gui/NaturalRegistryPickerScreen.java`
- 新增 `common/src/main/java/com/mobspawncontroller/client/gui/BlockIdListEditScreen.java`
- `common/src/main/resources/assets/mobspawncontroller/lang/zh_cn.json`
- `common/src/main/resources/assets/mobspawncontroller/lang/en_us.json`

## 详细方案

### 1. 新增 BlockIdListEditScreen

- 作为 `MobSpawnEditScreen` 的子界面，用于编辑某一个方块名单字段。
- 界面布局：标题、可滚动行列表、底部状态文本、保存/取消/验证按钮。
- 每行包含：
  - 一个 `EditBox`，输入方块 ID 或 `#block_tag`。
  - 一个删除按钮（X），点击移除当前行。
- 顶部或底部提供“新增一行”按钮（+）。
- 验证按钮：逐行检查：
  - 调用 `ResourceLocation.tryParse` 检查格式。
  - 非 tag 条目检查 `BuiltInRegistries.BLOCK.containsKey(id)`。
  - `#tag` 条目检查 `BuiltInRegistries.BLOCK.getTagNames()` 中是否存在对应 tag。
- 无效行用红色描边高亮，底部状态文本显示“无效方块 ID：xxx”。
- 点击保存时再次全量验证，若存在无效行则阻止返回并提示。
- 返回时把有效列表（去重、去空）写回父界面的 `naturalSelections`。

### 2. 修改 MobSpawnEditScreen

- `openNaturalPicker()` 中识别到 `block_below_list`、`block_at_list`、`block_above_list` 时，打开新的 `BlockIdListEditScreen`；其他 PICKER 字段继续走 `NaturalRegistryPickerScreen`。
- 保存流程 `saveAndClose()` 中，在发送网络包前对三个方块字段做最终验证：
  - 任一字段包含无效 ID 时，通过 `Minecraft.player.displayClientMessage` 显示红色提示，并终止保存。
  - 验证通过后才发送 `ServerboundSetNaturalSpawnPayload`。
- 移除原代码中用于方块字段的 `naturalPickerOptions()` 分支（或保留给未来可能的快速选择入口）。

### 3. 统一 CYCLE 控件风格

- 当前天气/难度等使用输入框内左右 `<`/`>` 箭头，颜色较暗。
- 改造为：
  - 左右两侧各一个 18×18 的箭头按钮区域。
  - 常态背景 `0xFF111827`，悬停 `0xFF1E293B`。
  - 箭头文字使用 `0xFF7DD3FC`，悬停 `0xFFFFFFFF`。
  - 当前值居中显示，保持使用 `gui.mobspawncontroller.natural.option.*` 翻译键。
  - 外框统一使用 `0xFF374151`（常态）/ `0xFF64748B`（悬停）/ `ACCENT_COLOR`（焦点）。
- 保持点击区域与现有逻辑一致，仅调整渲染。

### 4. 其他交互优化

- **Section 折叠箭头**：把当前文字箭头 `>` / `v` 替换为更清晰的 `▶` / `▼` 或 `+` / `-`（Unicode 小箭头），并在鼠标悬停时高亮。
- **Range 输入框**：当最小值大于最大值或输入非数字时，在保存验证中提示并阻止保存（当前静默忽略）。
- **Reset 按钮**：当前仅在自然生成页显示“重置”，点击后恢复默认值。保持该行为，但改进渲染：未修改时置灰，修改后显示高亮边框。

### 5. 本地化文本

- 利用已存在的 `gui.mobspawncontroller.natural.block_editor.*` 键：
  - `block_editor.description`、`block_editor.validate`、`block_editor.valid`、`block_editor.invalid`。
- 新增键：
  - `gui.mobspawncontroller.natural.block_editor.title`：编辑器标题。
  - `gui.mobspawncontroller.natural.block_editor.add`：新增一行。
  - `gui.mobspawncontroller.natural.block_editor.remove`：删除行（tooltip）。
  - `gui.mobspawncontroller.natural.block_editor.empty_hint`：空列表提示。
  - `gui.mobspawncontroller.natural.error.invalid_blocks`：保存时方块验证失败的提示。
  - `gui.mobspawncontroller.natural.error.range_min_max`：最小值大于最大值的提示。
- 同步更新 `zh_cn.json` 与 `en_us.json`。

## 不做的内容

- 本次不修改 `NaturalSpawnSettings` record、不新增生成条件字段、不改网络包结构、不改 JSON 存档格式。
- 服务端不再额外做方块 ID 验证（`MobSpawnManager` 已有 `ResourceLocation.tryParse` 容错；无效条目会在匹配时自然失效）。

## 未来可补充的生成条件清单

界面改造完成后，可向用户推荐以下候选条件，由用户挑选实施：

| 条件 | 类型 | 说明 |
|------|------|------|
| `on_ground` | CYCLE | 必须着地 / 必须悬空 / 不限。 |
| `colliding` / `can_spawn_here` | CYCLE | 是否与其他方块碰撞。 |
| `temperature` | RANGE | 当前生物群系温度范围。 |
| `downfall` | RANGE | 当前生物群系降水/湿度范围。 |
| `season` | CYCLE | 季节（需 SereneSeasons 兼容层）。 |
| `scoreboard_tags` | PICKER | 对生成生物或附近玩家检查计分板标签。 |
| `player_equipment` | PICKER | 附近玩家头盔/胸甲/手持物品。 |
| `world_day_count` | NUMBER | 世界总天数计数器（区别于当前 day 范围）。 |

## 验证步骤

1. 单人游戏打开生物生成控制器，进入任意生物的“自然生成”页。
2. 点击“脚下方块名单”，进入新编辑器：
   - 新增多行，分别输入 `minecraft:grass_block`、`#minecraft:logs`、`invalid:id::`。
   - 点击验证，确认前两行通过、第三行标红。
   - 修正后保存返回主编辑界面。
3. 输入错误方块 ID 后直接点主界面“保存”，确认出现红色提示且不退出。
4. 切换天气/难度/天空/流体/史莱姆区块，确认左右箭头风格一致。
5. 保存后退出重进，确认方块名单、CYCLE 选择持久化。
6. 确认维度/生物群系/结构选择器仍使用旧的多选列表，未受影响。
