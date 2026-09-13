package com.example.authenticator.feature.transfer.impl

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** UTC RFC 3339 时间格式化，仅导出负载使用；每次调用新建实例避免 SimpleDateFormat 线程安全问题。 */
internal object UtcTimeFormatter {
    private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    fun format(epochMillis: Long): String {
        val fmt = SimpleDateFormat(PATTERN, Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(epochMillis))
    }
}