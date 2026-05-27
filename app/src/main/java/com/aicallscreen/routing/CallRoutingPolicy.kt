package com.aicallscreen.routing

import com.aicallscreen.contacts.ContactResolver
import com.aicallscreen.contacts.PhoneNumberNormalizer
import com.aicallscreen.rules.GlobalScreeningMode
import com.aicallscreen.rules.ScreeningRulesRepository

class CallRoutingPolicy(
    private val contactResolver: ContactResolver,
    private val rulesRepository: ScreeningRulesRepository,
) {

    fun routeFor(phoneNumber: String): CallRoute {
        val rules = rulesRepository.currentRules()

        if (!rules.aiMasterEnabled || rules.globalMode == GlobalScreeningMode.AI_DISABLED) {
            return CallRoute.PASS_THROUGH
        }

        return when (rules.globalMode) {
            GlobalScreeningMode.DEFAULT -> defaultRoute(phoneNumber)
            GlobalScreeningMode.ALLOWLIST_ONLY -> {
                val normalized = PhoneNumberNormalizer.normalize(phoneNumber)
                if (normalized != null && rules.isAllowlisted(normalized)) {
                    defaultRoute(phoneNumber)
                } else {
                    CallRoute.PASS_THROUGH
                }
            }
            GlobalScreeningMode.AI_DISABLED -> CallRoute.PASS_THROUGH
        }
    }

    private fun defaultRoute(phoneNumber: String): CallRoute =
        if (contactResolver.isKnownContact(phoneNumber)) {
            CallRoute.KNOWN_CONTACT_NORMAL_RING
        } else {
            CallRoute.UNKNOWN_AI_SCREENING
        }
}
