package com.dramashield.app.service.rule

import com.dramashield.app.domain.model.InterceptRule
import com.dramashield.app.domain.model.RuleType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class RuleParseException(message: String) : Exception(message)

/**
 * 规则 JSON 解析器（文档 4.4）。
 * 导入时严格校验，避免损坏规则导致服务异常。
 */
object RuleJsonParser {

    const val MAX_RULES = 200
    private const val MAX_KEYWORDS = 50
    private const val MAX_KEYWORD_LENGTH = 50
    private const val MAX_PACKAGES = 20
    private const val MAX_PACKAGE_LENGTH = 100

    fun parse(json: String): List<InterceptRule> {
        val content = json.trim().removePrefix("\uFEFF").trim()
        if (content.isEmpty()) throw RuleParseException("规则内容为空")

        val array = try {
            JSONArray(content)
        } catch (e: JSONException) {
            throw RuleParseException("JSON 格式不正确")
        }

        if (array.length() == 0) throw RuleParseException("规则列表不能为空")
        if (array.length() > MAX_RULES) throw RuleParseException("规则数量不能超过 $MAX_RULES 条")

        val rules = ArrayList<InterceptRule>(array.length())
        val ids = HashSet<String>()

        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index)
                ?: throw RuleParseException("第 ${index + 1} 条规则格式不正确")

            val id = obj.optString("id", "").trim()
            if (id.isEmpty()) throw RuleParseException("第 ${index + 1} 条规则缺少 id")
            if (!ids.add(id)) throw RuleParseException("规则 id 重复：$id")

            val typeValue = obj.optString("type", "").trim()
            val type = RuleType.entries.firstOrNull {
                (it == RuleType.RED_VALLEY || it == RuleType.AD) &&
                    it.value.equals(typeValue, ignoreCase = true)
            } ?: throw RuleParseException("第 ${index + 1} 条规则 type 必须为 redvalley 或 ad")

            val keywords = parseStringArray(obj, "keywords", index)
            val closeKeywords = parseStringArray(obj, "closeKeywords", index)
            val enable = if (obj.has("enable")) obj.optBoolean("enable", true) else true
            val packages = parseOptionalStringArray(obj, "packages", MAX_PACKAGES)

            rules += InterceptRule(
                id = id,
                type = type,
                keywords = keywords,
                closeKeywords = closeKeywords,
                enable = enable,
                packages = packages
            )
        }
        return rules
    }

    fun parseLenient(json: String, fallback: List<InterceptRule>): List<InterceptRule> =
        runCatching { parse(json) }.getOrElse { fallback }

    fun toJson(rules: List<InterceptRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject()
            obj.put("id", rule.id)
            obj.put("type", rule.type.value)
            obj.put("keywords", JSONArray(rule.keywords))
            obj.put("closeKeywords", JSONArray(rule.closeKeywords))
            obj.put("enable", rule.enable)
            if (rule.packages.isNotEmpty()) {
                obj.put("packages", JSONArray(rule.packages))
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    private fun parseStringArray(obj: JSONObject, key: String, ruleIndex: Int): List<String> {
        val array = obj.optJSONArray(key)
            ?: throw RuleParseException("第 ${ruleIndex + 1} 条规则缺少 $key 数组")
        val values = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) {
                values += value.take(MAX_KEYWORD_LENGTH)
            }
        }
        if (values.isEmpty()) {
            throw RuleParseException("第 ${ruleIndex + 1} 条规则的 $key 不能为空")
        }
        return values.distinct().take(MAX_KEYWORDS)
    }

    private fun parseOptionalStringArray(
        obj: JSONObject,
        key: String,
        maxSize: Int
    ): List<String> {
        val array = obj.optJSONArray(key) ?: return emptyList()
        val values = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) {
                values += value.take(MAX_PACKAGE_LENGTH)
            }
        }
        return values.distinct().take(maxSize)
    }
}
