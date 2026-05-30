package com.andreas_kratzer.ghosttalk.ui.pages.actions

import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

data class PhoneNumberInfo(
    val number: String,
    val label: String
)

@Composable
fun rememberContactPickerLauncher(
    onContactSelected: (name: String, phone: String) -> Unit
): ManagedActivityResultLauncher<Void?, Uri?> {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var phoneNumbers by remember { mutableStateOf<List<PhoneNumberInfo>>(emptyList()) }
    var selectedName by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let { contactUri ->
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            )
            context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                    selectedName = name

                    val numbers = mutableListOf<PhoneNumberInfo>()
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.LABEL
                        ),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(id),
                        null
                    )?.use { phoneCursor ->
                        while (phoneCursor.moveToNext()) {
                            val number = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                            val type = phoneCursor.getInt(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
                            val label = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL))
                            
                            val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, type, label).toString()
                            numbers.add(PhoneNumberInfo(number, typeLabel))
                        }
                    }

                    when {
                        numbers.size > 1 -> {
                            phoneNumbers = numbers
                            showDialog = true
                        }
                        numbers.size == 1 -> {
                            onContactSelected(name, numbers[0].number)
                        }
                        else -> {
                            onContactSelected(name, "")
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        PhoneNumberPickerDialog(
            title = selectedName,
            phoneNumbers = phoneNumbers,
            onDismiss = { showDialog = false },
            onNumberSelected = { info ->
                onContactSelected(selectedName, info.number)
                showDialog = false
            }
        )
    }

    return launcher
}
