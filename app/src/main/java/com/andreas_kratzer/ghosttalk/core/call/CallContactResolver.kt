package com.andreas_kratzer.ghosttalk.core.call

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallContactResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CallContactResolver"
    }

    fun getContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) {
            Log.d(TAG, "getContactName: phone number is blank")
            return null
        }
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "getContactName: READ_CONTACTS permission is NOT granted!")
            return null
        }
        val cleanNumber = android.telephony.PhoneNumberUtils.stripSeparators(phoneNumber) ?: phoneNumber
        Log.d(TAG, "getContactName: Attempting lookup for raw='$phoneNumber', clean='$cleanNumber'")
        
        // 1. Primary Lookup: Use PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI to search personal and work contacts
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(cleanNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        try {
            Log.d(TAG, "getContactName: Querying PhoneLookup with URI: $uri")
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                Log.d(TAG, "getContactName: PhoneLookup cursor row count = ${cursor.count}")
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        Log.i(TAG, "getContactName: Found name '$name' via PhoneLookup")
                        return name
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getContactName: Exception during PhoneLookup", e)
        }

        // 2. Fallback: If PhoneLookup returned nothing, try CommonDataKinds.Phone.ENTERPRISE_CONTENT_FILTER_URI
        val fallbackUri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(cleanNumber))
        val fallbackProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        try {
            Log.d(TAG, "getContactName: Querying CommonDataKinds.Phone fallback with URI: $fallbackUri")
            context.contentResolver.query(fallbackUri, fallbackProjection, null, null, null)?.use { cursor ->
                Log.d(TAG, "getContactName: Fallback cursor row count = ${cursor.count}")
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        Log.i(TAG, "getContactName: Found name '$name' via Fallback Lookup")
                        return name
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getContactName: Exception during Fallback Lookup", e)
        }
        
        Log.i(TAG, "getContactName: No contact found for '$cleanNumber'")
        return null
    }
}
