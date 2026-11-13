package com.freecam.common.util

object Bytes {
    fun format(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun parseKb(kb: Int): Long = kb * 1024L
    fun parseMb(mb: Int): Long = mb * 1024L * 1024L
    fun parseGb(gb: Int): Long = gb * 1024L * 1024L * 1024L
}