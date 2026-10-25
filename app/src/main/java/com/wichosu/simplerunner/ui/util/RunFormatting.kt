package com.wichosu.simplerunner.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object RunFormatting {
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())

    /** 1730000000000 -> "Tue 22 Oct · 14:33" (locale dependent). */
    fun startedAt(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
}
