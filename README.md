# 离情梦 · 纯 UI 开源版

在原有工程基础上保留**完整界面**（悬浮窗 + 主页 + 全部页面/开关/标签），
删除全部网络、云端校验、卡密、native（内存读写）与开发工具，仅用于 UI 展示与二次开发。

## 保留的 UI

- 主页（Compose）：首页 / 关于 / 设置 / 背景 / 配色 / 悬浮窗设置
- 悬浮窗（View）：原有全部页签（首页 / 绘制 / 战斗 / 物品 / 特效 / 属性）、
  卡片、开关、滑块、齿轮子页、灵动岛提示外观
- 启动页、模式选择页、玻璃/模糊主题、动态取色、圆角卡片等视觉组件
- 原有图标、字体、背景视频等资源

## 已删除

| 分类 | 内容 |
|---|---|
| 云端 | `CloudConfig` 网络实现、`Secrets`、RC4 解密、公告/更新检查接口 |
| 卡密 | 网络验证 `AuthApi`、心跳 `Heartbeat`、卡密密钥与服务器地址（改为本地直通） |
| 内存 / native | 整个 `jni/`、`libmansui.so`、`libDLL.so`、`MansuiNative` JNI、`MemBridge` 路由、ROOT 服务（`MemoryRootService`、libsu 相关） |
| 调试工具 | Python 脚本、抓日志脚本、偏移/混淆文档、历史发布包 |
| 敏感信息 | 联系方式、内部项目文档、服务器地址与密钥 |

为保持 UI 原样编译，`MansuiNative`、`MemBridge`、`CloudConfig`、`AuthApi`、`Heartbeat`、`NoticeApi`
保留同名空实现：**函数存在、内部不做任何事**，不会加载 .so、不会联网、不会读写内存。

## 构建

环境：JDK 21、Android SDK（compileSdk 37）、Gradle 9.4.1（wrapper 自带）

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 说明

- 所有开关/滑块只修改界面状态，不产生实际功能
- 版本号保持 2.5 / 19
- 若二次开发：把空壳类替换成自己的实现即可，UI 层无需改动

## License

MIT
