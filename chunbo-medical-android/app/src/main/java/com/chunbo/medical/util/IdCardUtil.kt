package com.chunbo.medical.util

/**
 * 身份证号工具：18 位校验位校验 + 脱敏。
 * 校验算法遵循 GB 11643-1999（ISO 7064:1983 MOD 11-2）。
 */
object IdCardUtil {

    private val WEIGHTS = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    private val CHECK_CODES = charArrayOf('1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2')

    /** 校验 18 位身份证号（含校验位），15 位仅校验全数字 */
    fun isValid(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return when (id.length) {
            15 -> id.all { it.isDigit() }
            18 -> isValid18(id)
            else -> false
        }
    }

    private fun isValid18(id: String): Boolean {
        val body = id.substring(0, 17)
        if (!body.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until 17) {
            sum += (body[i] - '0') * WEIGHTS[i]
        }
        val expected = CHECK_CODES[sum % 11]
        val last = id[17].uppercaseChar()
        return last == expected
    }

    /** 脱敏：保留前 6 位 + 后 4 位，中间打码 */
    fun mask(id: String?): String {
        if (id.isNullOrBlank()) return ""
        if (id.length < 10) return id
        return id.substring(0, 6) + "********" + id.substring(id.length - 4)
    }
}
