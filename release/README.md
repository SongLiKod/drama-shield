# release

该目录用于存放发布产物与发布说明，签名文件、keystore 与 APK 不提交到 Git。

构建并签名发布包：

1. 参考 `keystore.properties.example` 在仓库根目录创建 `keystore.properties`。
2. 执行：

   ```bash
   ./gradlew assembleRelease
   ```

3. 已签名 APK 输出到 `app/build/outputs/apk/release/app-release.apk`；未配置签名时输出 `app-release-unsigned.apk`。

发布前请完成 README 中的测试清单，并确认版本号（`app/build.gradle.kts` 中的 `versionCode` / `versionName`）。
