package com.bluebubbles.messaging.services.system

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import android.util.Base64
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream


/// Open the new contact form picker
class NewContactFormRequestHandler: MethodCallHandlerImpl() {
    companion object {
        const val tag = "open-contact-form"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val address: String = call.argument("address")!!
        val addressType: String = call.argument("address_type")!!
        val name: String? = call.argument("name")
        val firstName: String? = call.argument("first_name")
        val middleName: String? = call.argument("middle_name")
        val lastName: String? = call.argument("last_name")
        val image: String? = call.argument("image")
        val existing: String? = call.argument("existing")

        var compressedImage = image?.let {
            var photoByteArray = Base64.decode(it, Base64.DEFAULT)
            val options = BitmapFactory.Options()
            val bitmap =
                BitmapFactory.decodeByteArray(photoByteArray, 0, photoByteArray.size, options)

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream) // 70% quality to shrink size

            stream.toByteArray()
        }

        if (existing != null) {
            ContactUpdater.updateContact(
                context,
                existing.toLong(),
                name!!,
                firstName,
                middleName,
                lastName,
                compressedImage
            )
            result.success(null)
            return
        }

        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT)
            .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            // Problem in Android 4.0+ (https://developer.android.com/training/contacts-provider/modify-data#add-the-navigation-flag)
            .putExtra("finishActivityOnSaveCompleted", true)
        if (addressType == "email") {
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, address)
        } else {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, address)
        }
        name?.let {
            intent.putExtra(ContactsContract.Intents.Insert.NAME, it)
        }

        val data = arrayListOf<ContentValues>()

        if (name != null) {
            data.add(
                ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    putNullableString(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                    putNullableString(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, middleName)
                    putNullableString(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
                }
            )
        }

        compressedImage?.let {
            data.add(
                ContentValues().apply {
                    put(ContactsContract.CommonDataKinds.Photo.PHOTO, it)
                    put(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    )
                }
            )
        }

        if (data.isNotEmpty()) {
            intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data)
        }

        context.startActivity(intent)
        result.success(null)
    }

    private fun ContentValues.putNullableString(key: String, value: String?) {
        if (value == null) {
            putNull(key)
        } else {
            put(key, value)
        }
    }
}
