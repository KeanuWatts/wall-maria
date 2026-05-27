package com.aicallscreen.rules

/**
 * User-configurable rules for when AI participates in calls.
 */
data class ScreeningRules(
    val aiMasterEnabled: Boolean = true,
    val globalMode: GlobalScreeningMode = GlobalScreeningMode.DEFAULT,
    val allowlist: List<AllowlistEntry> = emptyList(),
) {
    fun isAllowlisted(normalizedNumber: String): Boolean =
        allowlist.any { it.normalizedNumber == normalizedNumber }
}
