package com.aicallscreen.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

/**
 * Resolves whether an E.164/normalized number belongs to a saved contact.
 */
class ContactResolver(
    private val context: Context,
) {

    fun isKnownContact(phoneNumber: String): Boolean = resolveContact(phoneNumber) != null

    fun displayNameFor(phoneNumber: String): String? = resolveContact(phoneNumber)?.displayName

    fun ownerDisplayName(): String {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.Profile.CONTENT_URI,
            arrayOf(ContactsContract.Profile.DISPLAY_NAME),
            null,
            null,
            null,
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(ContactsContract.Profile.DISPLAY_NAME)
                if (index >= 0) {
                    val name = it.getString(index)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return context.getString(com.aicallscreen.R.string.default_user_name)
    }

    private fun resolveContact(phoneNumber: String): ResolvedContact? {
        if (!hasContactsPermission()) {
            Log.w(TAG, "READ_CONTACTS not granted — treating $phoneNumber as unknown")
            return null
        }

        val normalizedIncoming = PhoneNumberNormalizer.normalize(phoneNumber) ?: return null
        val resolver = context.contentResolver
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalizedIncoming),
        )

        val projection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.NUMBER,
        )

        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)
                val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val storedNumber = if (numberIndex >= 0) cursor.getString(numberIndex) else phoneNumber
                return ResolvedContact(
                    displayName = displayName ?: phoneNumber,
                    normalizedNumber = PhoneNumberNormalizer.normalize(storedNumber) ?: normalizedIncoming,
                )
            }
        }
        return null
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    data class ResolvedContact(
        val displayName: String,
        val normalizedNumber: String,
    )

    companion object {
        private const val TAG = "ContactResolver"
    }
}
