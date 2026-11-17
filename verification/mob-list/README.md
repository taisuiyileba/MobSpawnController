# 生物列表回归验证

在 Minecraft 的真实 `ServerLevel` 中运行界面使用的 `MobListFilter`，不启动客户端界面。

```powershell
python verification/mob-list/run.py --loader fabric --offline
python verification/mob-list/run.py --loader forge --offline
```

检查范围：

- 遍历实体注册表，铁傀儡、雪傀儡、村民和原有普通生物能进入列表。
- 玩家、盔甲架、掉落物、箭矢、船、矿车和交互实体被排除。
- 不依赖 ID 名单，自定义 `MISC` 僵尸子类也能识别。
- 识别和图标复用同一实例；空工厂、异常工厂的拒绝结果只计算一次。
- 缺少世界时不永久拒绝类型；关闭列表后清理正负缓存。
- 创建识别实例不会把实体加入世界。
- 两种傀儡和村民能发现血量、护甲控件，新生成实体实际应用血量 80、护甲 9。

输出在 `results/<loader>.json` 和 `results/<loader>.log`。服务器只监听 `127.0.0.1`。
世界及所有验证编译输出位于独立的 `build/mob-list-*` 目录；正常构建不会打包验证代码。

这项测试验证共享筛选逻辑和服务端属性生命周期，不表示已做客户端界面或任意第三方模组整合包实测。
