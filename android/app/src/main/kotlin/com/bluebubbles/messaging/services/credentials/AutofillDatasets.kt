package com.bluebubbles.messaging.services.credentials


import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation
import android.view.View
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import java.time.Instant
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import com.bluebubbles.messaging.R


@RequiresApi(Build.VERSION_CODES.O_MR1)
class AutofillDatasets {
    data class CreditCard (
        var creditCardNumber: String,
        var cardholderName: String,
        var expirationDate: YearMonth,
        var securityCode: String?,
    ) {
        enum class CardNetwork {
            VISA,
            MASTERCARD,
            AMEX,
            DISCOVER,
            UNKNOWN;

            override fun toString(): String {
                return when (this) {
                    DISCOVER -> "Discover"
                    VISA -> "Visa"
                    MASTERCARD -> "Mastercard"
                    AMEX -> "American Express"
                    UNKNOWN -> "Unknown"
                }
            }
        }

        fun detectNetwork(): CardNetwork {
            val pan = creditCardNumber
            val n = pan.replace(Regex("\\D"), "")
            val len = n.length

            if (n.startsWith("4") && len in setOf(13, 16, 19)) return CardNetwork.VISA

            if (len == 16) {
                val p2 = n.take(2).toInt()
                val p4 = n.take(4).toInt()
                if (p2 in 51..55 || p4 in 2221..2720) return CardNetwork.MASTERCARD
            }

            if (len == 15 && (n.startsWith("34") || n.startsWith("37"))) return CardNetwork.AMEX

            if (n.startsWith("6011") || n.startsWith("65")
                || n.take(3).toIntOrNull() in 644..649
            ) return CardNetwork.DISCOVER

            return CardNetwork.UNKNOWN
        }

        fun fillFields(context: Context, structure: AutofillStructure, inline: InlineSuggestionsRequest?): Dataset {
            val usernamePresentation = RemoteViews(context.packageName, R.layout.autofill_dataset)
            val card = "${detectNetwork()} •••• ${creditCardNumber.trim().takeLast(4)}"
            val subtitle = "Exp ${expirationDate.format(DateTimeFormatter.ofPattern("MM/yy"))} · $cardholderName"
            usernamePresentation.setTextViewText(R.id.autofill_dataset_title, card)
            usernamePresentation.setTextViewText(R.id.autofill_dataset_subtitle, subtitle)

            val dataset = Dataset.Builder(usernamePresentation)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inline != null) {
                val suggestion = InlineSuggestionUi.newContentBuilder(OBAutofillService.pendingClaifyIntent!!)
                    .setTitle(card)
                    .setSubtitle(subtitle)
                    .build()

                val uiversions: UiVersions.Content = suggestion
                // suggestion.slice = YOU'RE ACCESSING A RESTRICTED, INAPPROPRIATE API
                // uiversions.slice = totally documented, required way to display slices
                // see https://developer.android.com/reference/android/service/autofill/InlinePresentation
                dataset.setInlinePresentation(InlinePresentation(uiversions.slice, inline.inlinePresentationSpecs[0], true))
            }

            for (field in structure.fields) {
                when (field.second) {
                    AutofillStructure.AutofillType.CARD_NUMBER -> dataset.setValue(field.first.autofillId!!,
                        AutofillValue.forText(creditCardNumber))
                    AutofillStructure.AutofillType.CARD_NAME_FULL -> dataset.setValue(field.first.autofillId!!,
                        AutofillValue.forText(cardholderName))
                    AutofillStructure.AutofillType.CARD_VERIFICATION_CODE -> dataset.setValue(field.first.autofillId!!,
                        AutofillValue.forText(securityCode))
                    AutofillStructure.AutofillType.CARD_EXP_MONTH -> {
                        when (field.first.autofillType) {
                            View.AUTOFILL_TYPE_DATE -> dataset.setValue(field.first.autofillId!!,
                                AutofillValue.forDate(expirationDate
                                    .atDay(1)
                                    .atStartOfDay(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()))
                            View.AUTOFILL_TYPE_LIST -> {
                                val item = field.first.autofillOptions!!.indexOfFirst {
                                    it.toString() == expirationDate.month.value.toString() ||
                                            it.toString().padStart(2, '0') == expirationDate.month.value.toString() ||
                                            expirationDate.month.getDisplayName(TextStyle.FULL_STANDALONE,
                                                Locale.getDefault()).lowercase().startsWith(it.toString())
                                }
                                dataset.setValue(field.first.autofillId!!,
                                    AutofillValue.forList(item))
                            }
                            View.AUTOFILL_TYPE_TEXT -> dataset.setValue(field.first.autofillId!!,
                                AutofillValue.forText(expirationDate.month.toString().padStart(2, '0')))
                            else -> {}
                        }
                    }
                    AutofillStructure.AutofillType.CARD_EXP_4_DIGIT_YEAR -> {
                        when (field.first.autofillType) {
                            View.AUTOFILL_TYPE_DATE -> dataset.setValue(field.first.autofillId!!,
                                AutofillValue.forDate(expirationDate
                                    .atDay(1)
                                    .atStartOfDay(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()))
                            View.AUTOFILL_TYPE_LIST -> {
                                val item = field.first.autofillOptions!!.indexOfFirst { it.toString() == expirationDate.year.toString()  }
                                dataset.setValue(field.first.autofillId!!,
                                    AutofillValue.forList(item))
                            }
                            View.AUTOFILL_TYPE_TEXT -> dataset.setValue(field.first.autofillId!!,
                                AutofillValue.forText(expirationDate.year.toString()))
                            else -> {}
                        }
                    }
                    AutofillStructure.AutofillType.CARD_EXP_UNIFIED_2_DIGIT_YEAR -> {
                        when (field.first.autofillType) {
                            View.AUTOFILL_TYPE_DATE -> dataset.setValue(field.first.autofillId!!,
                                AutofillValue.forDate(expirationDate
                                    .atDay(1)
                                    .atStartOfDay(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()))
                            View.AUTOFILL_TYPE_TEXT -> dataset.setValue(field.first.autofillId!!,
                                AutofillValue.forText(expirationDate.format(DateTimeFormatter.ofPattern("MM/yy"))))
                            else -> {}
                        }
                    }
                    AutofillStructure.AutofillType.CARD_TYPE -> {
                        val item = field.first.autofillOptions!!.indexOfFirst { it.toString().lowercase() == detectNetwork().name.lowercase()  }
                        dataset.setValue(field.first.autofillId!!,
                            AutofillValue.forList(item))
                    }
                    else -> {}
                }
            }

            return dataset.build()
        }

        fun parseYear(input: String): Int {
            val s = input.trim()
            require(s.matches(Regex("\\d{2,4}"))) { "Invalid year: $input" }

            val y = s.toInt()
            return when (s.length) {
                4 -> y
                2 -> if (y >= 80) 1900 + y else 2000 + y
                else -> error("unreachable")
            }
        }

        fun parseMmYy(input: String): YearMonth {
            val s = input.trim()
            require(s.matches(Regex("\\d{2}/\\d{2}"))) { "Invalid MM/YY: $input" }

            val month = s.substring(0, 2).toInt()
            require(month in 1..12)

            val yy = s.substring(3, 5).toInt()
            val year = if (yy >= 80) 1900 + yy else 2000 + yy

            return YearMonth.of(year, month)
        }

        fun importFields(structure: AutofillStructure) {
            var month: Month? = null
            var year: String? = null
            for (field in structure.fields) {
                if (field.first.autofillType == View.AUTOFILL_TYPE_TEXT && field.first.autofillValue!!.textValue.toString().trim() == "") continue
                when (field.second) {
                    AutofillStructure.AutofillType.CARD_NUMBER -> field.first.autofillValue?.let { creditCardNumber = it.textValue.toString() }
                    AutofillStructure.AutofillType.CARD_NAME_FULL -> field.first.autofillValue?.let { cardholderName = it.textValue.toString() }
                    AutofillStructure.AutofillType.CARD_VERIFICATION_CODE -> field.first.autofillValue?.let { securityCode = it.textValue.toString() }
                    AutofillStructure.AutofillType.CARD_EXP_MONTH -> {
                        if (field.first.autofillValue == null) return;
                        when (field.first.autofillType) {
                            View.AUTOFILL_TYPE_DATE -> {
                                expirationDate = Instant.ofEpochMilli(field.first.autofillValue!!.dateValue)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .let { YearMonth.from(it) }
                            }
                            View.AUTOFILL_TYPE_LIST -> month = Month.of(field.first.autofillValue!!.listValue + 1)
                            View.AUTOFILL_TYPE_TEXT -> month = Month.of(field.first.autofillValue!!.textValue.toString().toInt())
                            else -> {}
                        }
                    }
                    AutofillStructure.AutofillType.CARD_EXP_4_DIGIT_YEAR -> {
                        when (field.first.autofillType) {
                            View.AUTOFILL_TYPE_DATE -> {
                                expirationDate = Instant.ofEpochMilli(field.first.autofillValue!!.dateValue)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .let { YearMonth.from(it) }
                            }
                            View.AUTOFILL_TYPE_LIST -> {
                                year = field.first.autofillOptions!![field.first.autofillValue!!.listValue].toString()
                            }
                            View.AUTOFILL_TYPE_TEXT -> {
                                year = field.first.autofillValue!!.textValue.toString()
                            }
                            else -> {}
                        }
                    }
                    AutofillStructure.AutofillType.CARD_EXP_UNIFIED_2_DIGIT_YEAR -> {
                        when (field.first.autofillType) {
                            View.AUTOFILL_TYPE_DATE -> {
                                expirationDate = Instant.ofEpochMilli(field.first.autofillValue!!.dateValue)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .let { YearMonth.from(it) }
                            }
                            View.AUTOFILL_TYPE_TEXT -> {
                                expirationDate = parseMmYy(field.first.autofillValue!!.textValue.toString())
                            }
                            else -> {}
                        }
                    }
                    else -> {}
                }
            }
            if (month != null && year != null) {
                expirationDate = YearMonth.of(parseYear(year), month)
            }
        }
    }

    data class LoginInfo(
        var username: String,
        var password: String,
        val domain: String,
        val otp: String?,
    ) {
        fun fillFields(context: Context, structure: AutofillStructure, inline: InlineSuggestionsRequest?): Dataset {
            val usernamePresentation = RemoteViews(context.packageName, R.layout.autofill_dataset)
            usernamePresentation.setTextViewText(R.id.autofill_dataset_title, username)
            usernamePresentation.setTextViewText(R.id.autofill_dataset_subtitle, domain)

            val dataset = Dataset.Builder(usernamePresentation)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inline != null) {
                val suggestion = InlineSuggestionUi.newContentBuilder(OBAutofillService.pendingClaifyIntent!!)
                    .setTitle(username)
                    .setSubtitle(domain)
                    .build()

                val uiversions: UiVersions.Content = suggestion
                // suggestion.slice = YOU'RE ACCESSING A RESTRICTED, INAPPROPRIATE API
                // uiversions.slice = totally documented, required way to display slices
                // see https://developer.android.com/reference/android/service/autofill/InlinePresentation
                dataset.setInlinePresentation(InlinePresentation(uiversions.slice, inline.inlinePresentationSpecs[0], true))
            }

            for (field in structure.fields) {
                when (field.second) {
                    AutofillStructure.AutofillType.EMAIL -> dataset.setValue(field.first.autofillId!!,
                        AutofillValue.forText(username))
                    AutofillStructure.AutofillType.PASSWORD -> dataset.setValue(field.first.autofillId!!,
                        AutofillValue.forText(password))
                    AutofillStructure.AutofillType.OTP -> otp?.let {
                        dataset.setValue(field.first.autofillId!!,
                            AutofillValue.forText(it))
                    }
                    else -> {}
                }
            }

            return dataset.build()
        }

        fun importFields(structure: AutofillStructure) {
            for (field in structure.fields) {
                if (field.first.autofillType == View.AUTOFILL_TYPE_TEXT && field.first.autofillValue!!.textValue.toString().trim() == "") continue
                when (field.second) {
                    AutofillStructure.AutofillType.EMAIL -> field.first.autofillValue?.let { username = it.textValue.toString() }
                    AutofillStructure.AutofillType.PASSWORD -> field.first.autofillValue?.let { password = it.textValue.toString() }
                    else -> {}
                }
            }
        }
    }
}