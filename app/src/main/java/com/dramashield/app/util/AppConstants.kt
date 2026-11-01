package com.dramashield.app.util

object AppConstants {
    const val REPO_URL = "https://SongLiKod.com/SongLiKod/drama-shield"

    const val MIN_SCAN_INTERVAL_MS = 200L
    const val MAX_SCAN_INTERVAL_MS = 2000L
    const val DEFAULT_SCAN_INTERVAL_MS = 500L
    const val SCAN_INTERVAL_STEP_MS = 100L

    const val MAX_CUSTOM_FILTER_KEYWORDS = 100
    const val MAX_CUSTOM_FILTER_LENGTH = 30

    /** 内置自定义跳过文字，用户可逐条删除，也可一键恢复。 */
    val DEFAULT_CUSTOM_FILTER_KEYWORDS: Set<String> = linkedSetOf(
        "查看详情",
        "点击进入游戏",
        "点击进入直播间",
        "极速下载",
        "立即下载",
        "免费下载",
        "下载领取",
        "应用点亮",
        "上滑继续观看短剧",
        "上滑继续看短剧"
    )

    const val DISCLAIMER =
        "本工具仅提供本地广告跳过与弹窗过滤，不提供影视资源，不破解付费内容。使用本工具产生的任何风险与后果由用户自行承担。"

    const val PRIVACY_NOTE =
        "所有配置、规则与跳过日志均仅保存在本机，不上传任何用户数据，也不会申请通讯录、位置等无关敏感权限。"

    const val ABOUT_DESCRIPTION =
        "短剧护盾依靠无障碍服务，自动识别并跳过短剧播放中的广告，同时支持红包/广告弹窗过滤，让观看体验更清爽。"
}
