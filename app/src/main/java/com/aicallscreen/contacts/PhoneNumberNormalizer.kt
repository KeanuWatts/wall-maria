package com.aicallscreen.contacts

/**
 * Strips formatting so contact lookups are stable across locales.
 */
object PhoneNumberNormalizer {

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digitsOnly = raw.filter { it.isDigit() || it == '+' }
        if (digitsOnly.isBlank()) return null
        return if (digitsOnly.startsWith("+")) digitsOnly else digitsOnly.trimStart('0')
    }
}
