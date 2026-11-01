package com.dramashield.app.service.rule

import com.dramashield.app.domain.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleJsonParserTest {

    @Test
    fun `default rules serialize and parse back`() {
        val json = RuleJsonParser.toJson(BuiltInRules.DEFAULT_RULES)
        val parsed = RuleJsonParser.parse(json)

        assertEquals(4, parsed.size)
        assertEquals("hongguo_ad_001", parsed[0].id)
        assertEquals(RuleType.AD, parsed[0].type)
        assertEquals(listOf("com.phoenix.read"), parsed[0].packages)
        assertEquals("hongguo_red_001", parsed[1].id)
        assertEquals(RuleType.RED_VALLEY, parsed[1].type)
        assertEquals(listOf("com.phoenix.read"), parsed[1].packages)

        assertEquals("redvalley_001", parsed[2].id)
        assertEquals(RuleType.RED_VALLEY, parsed[2].type)
        assertEquals(listOf("红谷", "红包", "领取红包"), parsed[2].keywords)
        assertEquals(listOf("关闭", "×", "跳过"), parsed[2].closeKeywords)
        assertEquals("ad_001", parsed[3].id)
        assertEquals(RuleType.AD, parsed[3].type)
        assertTrue(parsed.all { it.enable })

        val hongguoRed = parsed.first { it.id == "hongguo_red_001" }
        assertTrue(hongguoRed.keywords.contains("红包"))

        val hongguoAd = parsed.first { it.id == "hongguo_ad_001" }
        assertTrue(hongguoAd.keywords.contains("广告"))
    }

    @Test
    fun `custom rule with disabled state is preserved`() {
        val json = """
            [
              {
                "id": "custom_1",
                "type": "ad",
                "keywords": ["插屏"],
                "closeKeywords": ["关闭"],
                "enable": false
              }
            ]
        """.trimIndent()

        val parsed = RuleJsonParser.parse(json)

        assertEquals(1, parsed.size)
        assertFalse(parsed[0].enable)
        assertEquals("custom_1", parsed[0].id)
    }

    @Test
    fun `unknown type is rejected`() {
        val exception = assertThrows(RuleParseException::class.java) {
            RuleJsonParser.parse(
                """
                [{"id":"x","type":"banner","keywords":["广告"],"closeKeywords":["关闭"]}]
                """.trimIndent()
            )
        }
        assertTrue(exception.message.orEmpty().contains("redvalley"))
    }

    @Test
    fun `duplicate ids are rejected`() {
        assertThrows(RuleParseException::class.java) {
            RuleJsonParser.parse(
                """
                [
                  {"id":"same","type":"ad","keywords":["广告"],"closeKeywords":["关闭"]},
                  {"id":"same","type":"redvalley","keywords":["红包"],"closeKeywords":["关闭"]}
                ]
                """.trimIndent()
            )
        }
    }

    @Test
    fun `empty keywords are rejected`() {
        assertThrows(RuleParseException::class.java) {
            RuleJsonParser.parse(
                """
                [{"id":"x","type":"ad","keywords":[],"closeKeywords":["关闭"]}]
                """.trimIndent()
            )
        }
    }

    @Test
    fun `lenient parser falls back on invalid json`() {
        val fallback = BuiltInRules.DEFAULT_RULES
        val result = RuleJsonParser.parseLenient("{not-json", fallback)
        assertEquals(fallback, result)
    }

    @Test
    fun `too many rules are rejected`() {
        val tooMany = (0..RuleJsonParser.MAX_RULES).joinToString(prefix = "[", postfix = "]") { index ->
            """{"id":"r$index","type":"ad","keywords":["广告"],"closeKeywords":["关闭"]}"""
        }
        assertThrows(RuleParseException::class.java) {
            RuleJsonParser.parse(tooMany)
        }
    }
}
