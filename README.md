# 短剧护盾 - DramaShield

> 一款 Android 端短剧广告净化辅助工具。依靠无障碍服务，自动识别并跳过短剧播放中的广告，同时支持红果/红谷红包与插屏广告弹窗过滤。
>
> 应用名称：短剧护盾　窗口标题：短剧护盾 - DramaShield　包名：`com.dramashield.app`

## 功能

- **播放广告自动跳过**：识别红果等短剧应用的广告播放页、激励广告、开屏广告；识别到广告后只执行**向上滑动**手势，直接进入下一集，不点击广告页上的关闭/奖励/优惠券按钮。
- **关闭广告后自动播放下一集**：上滑动作同时完成“跳过广告 + 进入下一集”；首页和设置页均可独立开关。
- **红果内置规则**：内置红果免费短剧（`com.phoenix.read`）专用广告/红包识别规则；广告跳过依赖广告页识别与上滑手势，不依赖点击广告按钮。
- **自定义跳过文字**：在规则页直接输入文字列表（如“广告”“跳过”“开宝箱”），无需手写 JSON；仅用于弹窗过滤，播放广告跳过不依赖关键词。
- **弹窗过滤（可选）**：保留红谷红包、插屏广告弹窗的关键词识别与自动关闭。
- **总开关与白名单**：一键开启 / 关闭；仅白名单应用在前台时执行检测，其他应用直接跳过。
- **规则导入导出**：内置基础规则，支持本地 JSON 规则导入 / 导出、单条规则启停、规则命中统计。
- **跳过日志**：记录时间、包名、应用名、类型、规则 ID、是否成功；支持分页、类型筛选、清空、导出 JSON。
- **权限与后台引导**：首次启动引导无障碍授权、状态实时检测、服务断开提示与一键跳转、电池优化引导。
- **跟随系统主题**：仅跟随系统浅色 / 深色，App 内不提供独立主题开关；底部四 Tab：首页、规则、日志、设置。
- **全部数据本地存储**：DataStore 保存配置、Room 保存日志，不上传用户数据。

## 平台与技术栈

| 项目 | 说明 |
| --- | --- |
| 平台 | Android，最低 API 29（Android 10），编译 API 35 |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3，单 Activity |
| 架构 | MVVM + 分层架构 |
| DI | Hilt |
| 异步 | Coroutine + Flow |
| 配置 | DataStore（Preferences） |
| 日志 | Room + Paging 3 |
| 核心能力 | Android AccessibilityService |
| 构建 | Gradle Kotlin DSL |

## 构建

环境要求：JDK 17、Android SDK Platform 35、Build Tools 35。

```bash
# 调试包
./gradlew assembleDebug

# 发布包（未配置签名时生成 app-release-unsigned.apk）
./gradlew assembleRelease
```

构建产物：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`

### 本地签名

签名文件不提交到 Git。复制 `keystore.properties.example` 为 `keystore.properties` 并填写本地 keystore 信息，重新执行 `./gradlew assembleRelease` 即可生成已签名发布包。

```properties
storeFile=../keystore/drama-shield.jks
storePassword=your-store-password
keyAlias=drama-shield
keyPassword=your-key-password
```

## 项目结构

```
app/src/main/java/com/dramashield/app
├── ui
│   ├── navigation        # Compose Navigation 路由与底部四 Tab
│   ├── screen            # Home / Rule / Log / Setting / About
│   ├── components        # 卡片、开关、状态徽章、空状态、免责声明等
│   └── theme             # Material3 主题、颜色、字体、形状
├── domain
│   ├── model             # 拦截记录、规则实体、AppItem
│   └── repository        # 仓库接口
├── data
│   ├── repository        # 仓库实现
│   └── local             # DataStore、Room Entity / DAO / Database
├── service
│   ├── DramaShieldAccessibilityService.kt
│   ├── detector          # 节点扫描、RedValleyDetector、AdPopupDetector
│   ├── action            # 节点点击 / 手势点击兜底
│   └── rule              # JSON 规则解析与内置规则
└── util
    ├── permission        # 无障碍检测与引导
    ├── battery           # 电池优化引导
    ├── file              # SAF 文件读写
    └── log               # 日志工具
```

## 规则 JSON

内置规则与文档一致：

```json
[
  {
    "id": "redvalley_001",
    "type": "redvalley",
    "keywords": ["红谷", "红包", "领取红包"],
    "closeKeywords": ["关闭", "×", "跳过"],
    "enable": true
  },
  {
    "id": "ad_001",
    "type": "ad",
    "keywords": ["广告", "倒计时", "立即领取"],
    "closeKeywords": ["关闭", "跳过"],
    "enable": true
  }
]
```

导入规则时会严格校验 JSON 结构、类型、ID 唯一性与关键词非空；导入规则与现有规则按 `id` 合并（同 ID 覆盖）。导入前会展示风险提示。

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `BIND_ACCESSIBILITY_SERVICE`（服务声明） | 弹窗识别与自动点击的核心能力 |
| `FOREGROUND_SERVICE` | 按技术文档声明 |
| `WRITE_EXTERNAL_STORAGE`（maxSdkVersion 32） | 兼容旧系统文件导出 |
| `READ_MEDIA_IMAGES` | 按技术文档声明 |

文件导入 / 导出均通过 SAF（Storage Access Framework）完成，适配 Android 13+ 存储限制。应用不申请通讯录、位置等权限，也不申请网络权限；所有配置、规则、日志均保存在本机。

## 测试清单

- [ ] 播放红果短剧时出现广告，护盾自动跳过/关闭，返回正常播放或信息流。
- [ ] 红果开屏广告、激励视频广告、直播间广告均可自动退出。
- [ ] 自定义跳过文字可添加、删除并持久化，无需 JSON。
- [ ] 系统切换浅色 / 深色，App 自动跟随，无样式错乱。
- [ ] 未授予无障碍权限：开关置灰，首页提示清晰，可一键跳转设置。
- [ ] 不在白名单的应用前台时，检测器不工作、不产生日志。
- [ ] 规则导入 / 导出、日志导出 / 清空正常。
- [ ] 后台静置后验证无障碍服务存活；服务被终止后首页显示“断开”并可重新开启。
- [ ] 多分辨率、折叠屏与横竖屏切换下布局无溢出、截断。

## 已知限制

- 广告跳过采用向上滑动手势，不点击广告页按钮；如果某些广告页不响应上滑，可继续回传截图适配识别条件。
- 部分激励广告必须等待倒计时结束才能离开，护盾会根据识别结果重试上滑，但无法绕过广告平台的强制计时。
- 国内厂商后台策略严格，无障碍服务可能被系统终止，建议引导用户关闭电池优化。
- WebView 内弹窗通常无法读取节点文本，识别率会下降。
- 短剧厂商可能频繁修改广告页文案、跳过按钮和控件 ID，规则需要持续维护。
- 无障碍类应用审核受限，本项目仅适合开源分发，不上架国内应用商店。

## 文档

- [需求文档](docs/需求文档.md)
- [技术开发文档](docs/技术文档.md)

> 根目录的 `需求文档.md`、`技术文档.md` 为同一份文档的副本，内容以 `docs/` 目录为准。

## 免责声明

本工具仅提供本地弹窗过滤，不提供影视资源，不破解付费内容。使用本工具产生的任何风险与后果由用户自行承担。

## License

[MIT](LICENSE)
