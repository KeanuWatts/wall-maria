package com.aicallscreen.routing

import com.aicallscreen.contacts.ContactResolver

class CallRoutingPolicy(
    private val contactResolver: ContactResolver,
) {

    fun routeFor(phoneNumber: String): CallRoute =
        if (contactResolver.isKnownContact(phoneNumber)) {
            CallRoute.KNOWN_CONTACT_NORMAL_RING
        } else {
            CallRoute.UNKNOWN_AI_SCREENING
        }
}
