package com.shopmanager.chat.util

import java.math.BigDecimal
import java.math.RoundingMode

/** 1234567.5 -> "12,34,567" (Indian grouping). */
fun formatIndian(value: Double, decimals: Int = 0): String {
    val bd = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
    val plain = bd.abs().toPlainString()
    val intPart = plain.substringBefore('.')
    val frac = if ('.' in plain) plain.substringAfter('.') else ""
    val sign = if (bd.signum() < 0) "-" else ""
    return sign + groupIndian(intPart) + if (frac.isNotEmpty()) ".$frac" else ""
}

fun rupees(value: Double, decimals: Int = 0): String = "₹" + formatIndian(value, decimals)

/** Groups a string of digits as 12,34,567. */
fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3)
    val tail = digits.takeLast(3)
    return head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
}

private val AMOUNT = Regex("""(₹|Rs\.?|INR)(\s?)(\d+(?:,\d+)*)(\.\d+)?""", RegexOption.IGNORE_CASE)

/** Re-groups currency amounts in server text, e.g. "₹1,234,567.50" -> "₹12,34,567.50". */
fun localizeAmounts(text: String): String = AMOUNT.replace(text) { m ->
    val digits = m.groupValues[3].replace(",", "")
    m.groupValues[1] + m.groupValues[2] + groupIndian(digits) + m.groupValues[4]
}
