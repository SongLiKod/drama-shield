# 保留 Room / Hilt 生成代码的默认规则由库自带 consumer rules 提供。
# 这里只保留导出模型字段名，避免发布包 JSON 字段被混淆。
-keepclassmembers class com.dramashield.app.domain.model.** {
    <fields>;
}
