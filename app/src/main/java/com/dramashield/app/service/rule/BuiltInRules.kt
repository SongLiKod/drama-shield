package com.dramashield.app.service.rule

import com.dramashield.app.domain.model.InterceptRule
import com.dramashield.app.domain.model.RuleType
import com.dramashield.app.util.KnownPackages

/**
 * 内置基础规则。红果专用规则优先展示；文档 4.4 的通用红包/广告规则保留。
 */
object BuiltInRules {

    val DEFAULT_RULES: List<InterceptRule> = listOf(
        // 红果免费短剧专用：插屏广告、倒计时广告、奖励视频弹窗。
        InterceptRule(
            id = "hongguo_ad_001",
            type = RuleType.AD,
            keywords = listOf(
                "广告",
                "跳过广告",
                "倒计时",
                "看视频再领",
                "领取奖励",
                "开宝箱",
                "金币奖励",
                "金币",
                "签到",
                "今日签到",
                "立即签到",
                "今日可领",
                "立即领取",
                "去赚钱",
                "去提现",
                "下载",
                "安装",
                "打开应用"
            ),
            closeKeywords = listOf(
                "关闭",
                "跳过",
                "×",
                "✕",
                "取消",
                "我知道了",
                "以后再说"
            ),
            enable = true,
            packages = listOf(KnownPackages.HONGGUO_FREE_DRAMA)
        ),
        // 红果免费短剧专用：红果红包弹窗。
        InterceptRule(
            id = "hongguo_red_001",
            type = RuleType.RED_VALLEY,
            keywords = listOf(
                "红果发来的红包",
                "恭喜发财",
                "大吉大利",
                "红包",
                "金币"
            ),
            closeKeywords = listOf("关闭", "×", "✕", "跳过", "取消", "我知道了"),
            enable = true,
            packages = listOf(KnownPackages.HONGGUO_FREE_DRAMA)
        ),
        InterceptRule(
            id = "redvalley_001",
            type = RuleType.RED_VALLEY,
            keywords = listOf("红谷", "红包", "领取红包"),
            closeKeywords = listOf("关闭", "×", "跳过"),
            enable = true
        ),
        InterceptRule(
            id = "ad_001",
            type = RuleType.AD,
            keywords = listOf("广告", "倒计时", "立即领取"),
            closeKeywords = listOf("关闭", "跳过"),
            enable = true
        )
    )
}
