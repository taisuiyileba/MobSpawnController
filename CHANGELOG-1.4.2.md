### 1.4.2 更新日志 / Changelog

#### 中文

- 修复铁傀儡、雪傀儡、村民等生物在配置列表中缺少修改入口的问题。现在按实际生物类型识别，也能显示归类到 `MISC` 的模组生物；缓存识别结果和预览实例，并隔离实体创建异常。
- 为血量、护甲、攻击、速度等常见属性添加原版 HUD 和物品图标，统一图标、文字与输入框的对齐，图标可随资源包变化。
- 属性列表优先显示原版属性，再显示模组属性；各组内按属性 ID 排序。

#### English

- Fixed missing configuration entries for iron golems, snow golems, villagers, and other mobs. The list now identifies actual mob types, including modded mobs registered as `MISC`, caches classification results and preview instances, and isolates entity construction failures.
- Added vanilla HUD and item icons for common attributes such as health, armor, attack damage, and movement speed. Icons, labels, and input fields stay aligned, and the icons follow resource packs.
- Vanilla attributes now appear before modded attributes, with each group sorted by attribute ID.
