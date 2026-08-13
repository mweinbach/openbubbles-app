package com.bluebubbles.messaging.services.credentials


import android.app.assist.AssistStructure
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.autofill.FillResponse
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O_MR1)
class AutofillStructure(context: Context, structure: AssistStructure) {
    var lastString = ""
    var webDomain: String? = null

    interface Matcher {
        fun matches(text: String): Boolean
    }

    class Lit(val lit: String): Matcher {
        override fun matches(text: String): Boolean {
            return text.lowercase().contains(lit.lowercase())
        }
    }
    class Exc(val lit: String): Matcher {
        override fun matches(text: String): Boolean {
            return !text.lowercase().contains(lit.lowercase())
        }
    }

    class And(vararg val matchers: Matcher): Matcher {
        override fun matches(text: String): Boolean {
            return matchers.all { it.matches(text) }
        }
    }

    class Or(vararg val matchers: Matcher): Matcher {
        override fun matches(text: String): Boolean {
            return matchers.any { it.matches(text) }
        }
    }

    enum class AutofillType (
        val uaHint: String?,
        val computedHint: String?,
        val extraHints: Matcher,
        val acceptableTypes: List<Int>,
    ) {
        CARD_NAME_FULL(
            "CREDIT_CARD_NAME_FULL",
            "HTML_TYPE_CREDIT_CARD_NAME_FULL",
            And(Or(Lit("cc"), Lit("card")), Lit("name"), Exc("nick")), // no nick name
            listOf(View.AUTOFILL_TYPE_TEXT)
        ),
        CARD_TYPE(
            "CREDIT_CARD_TYPE",
            "HTML_TYPE_CREDIT_CARD_TYPE",
            And(Or(Lit("cc"), Lit("card")), Lit("type")),
            listOf(View.AUTOFILL_TYPE_LIST)
        ),
        CARD_NUMBER(
            "CREDIT_CARD_NUMBER",
            "HTML_TYPE_CREDIT_CARD_NUMBER",
            And(Or(Lit("cc"), Lit("card")), Lit("number")),
            listOf(View.AUTOFILL_TYPE_TEXT)
        ),
        CARD_VERIFICATION_CODE(
            "CREDIT_CARD_VERIFICATION_CODE",
            "HTML_TYPE_CREDIT_CARD_VERIFICATION_CODE",
            Or(Lit("cvv"), Lit("cvc"), Lit("security")),
            listOf(View.AUTOFILL_TYPE_TEXT)
        ),
        CARD_EXP_MONTH(
            "CREDIT_CARD_EXP_MONTH",
            "HTML_TYPE_CREDIT_CARD_EXP_MONTH",
            And(Lit("exp"), Or(Lit("month"), Lit("mm")), Exc("yy"), Exc("Year")),
            listOf(View.AUTOFILL_TYPE_TEXT, View.AUTOFILL_TYPE_LIST, View.AUTOFILL_TYPE_DATE)
        ),
        CARD_EXP_4_DIGIT_YEAR(
            "CREDIT_CARD_EXP_4_DIGIT_YEAR",
            "HTML_TYPE_CREDIT_CARD_EXP_YEAR",
            And(Lit("exp"), Or(Lit("year"), Lit("yyyy")), Exc("mm"), Exc("Month")),
            listOf(View.AUTOFILL_TYPE_TEXT, View.AUTOFILL_TYPE_LIST, View.AUTOFILL_TYPE_DATE)
        ),
        CARD_EXP_UNIFIED_2_DIGIT_YEAR(
            "CREDIT_CARD_EXP_DATE_2_DIGIT_YEAR",
            "HTML_TYPE_CREDIT_CARD_EXP",
            And(Lit("exp"),
                Or(And(Or(Lit("year"), Lit("yy")), Or(Lit("month"), Lit("mm"))), Lit("date"))),
            listOf(View.AUTOFILL_TYPE_TEXT, View.AUTOFILL_TYPE_DATE)
        ),
        EMAIL(
            null,
            "HTML_TYPE_EMAIL",
            Or(Lit("Email"), Lit("Username")),
            listOf(View.AUTOFILL_TYPE_TEXT)
        ),
        PASSWORD(
            null,
            null,
            Lit("Password"),
            listOf(View.AUTOFILL_TYPE_TEXT)
        ),
        OTP(
            null,
            null,
            Or(Lit("otp"), Lit("verification code"), Lit("two-factor"), Lit("two factor"), Lit("XXXXXX")),
            listOf(View.AUTOFILL_TYPE_TEXT)
        )
    }

    val fields = arrayListOf<Pair<AssistStructure.ViewNode, AutofillType>>()

    fun hasCreditCards(): Boolean {
        return fields.any { it.second == AutofillType.CARD_NUMBER }
    }

    fun getEmailSaveFields(): List<AutofillId> {
        return fields
            .filter {
                it.second == AutofillType.EMAIL ||
                        it.second == AutofillType.PASSWORD
            }
            .map { it.first.autofillId!! }
    }

    fun getCreditCardsFields(): List<AutofillId> {
        return fields
            .filter {
                it.second == AutofillType.CARD_NUMBER ||
                        it.second == AutofillType.CARD_TYPE ||
                        it.second == AutofillType.CARD_NAME_FULL ||
                        it.second == AutofillType.CARD_VERIFICATION_CODE ||
                        it.second == AutofillType.CARD_EXP_MONTH ||
                        it.second == AutofillType.CARD_EXP_4_DIGIT_YEAR ||
                        it.second == AutofillType.CARD_EXP_UNIFIED_2_DIGIT_YEAR
            }
            .map { it.first.autofillId!! }
    }

    fun hasEmails(): Boolean {
        return fields.any { it.second == AutofillType.EMAIL || it.second == AutofillType.PASSWORD || it.second == AutofillType.OTP }
    }

    val packageMapping = mapOf<String, String>()


    init {
        val pn = structure.activityComponent.packageName
        getPackageDomain(context,pn)
        webDomain = packageMapping[pn]

        for (i in 0..<structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            processNode(windowNode.rootViewNode)
        }
    }

    fun getPackageDomain(context: Context, packageName: String) {
//        val ai = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
//        val md = ai.metaData ?: return
//
//        val string = md.getString("asset_statements") ?: return
//        Log.i("statements", string)
    }

    fun processNode(node: AssistStructure.ViewNode) {
        val currentText = "${node.text ?: ""}${node.hint ?: ""}${node.contentDescription ?: ""}".trim()
        if (node.autofillId != null) {
            // this is a form
            val properties = node.htmlInfo?.attributes?.associate { it.first to it.second }.orEmpty()

            var type = AutofillType.entries.filter { candidate -> candidate.acceptableTypes.contains(node.autofillType) }.find { candidate ->
                if (candidate.uaHint != null && (candidate.uaHint == properties["ua-autofill-hints"] || candidate.uaHint == properties["computed-autofill-hints"])) {
                    return@find true
                }
                if (candidate.computedHint != null && (candidate.computedHint == properties["ua-autofill-hints"] || candidate.computedHint == properties["computed-autofill-hints"])) {
                    return@find true
                }
                false
            }

            Log.i("type", "$type")

            if (type == null) {
                type = AutofillType.entries.filter { candidate -> candidate.acceptableTypes.contains(node.autofillType) }.find { candidate ->
                    val allowedKeys = listOf("name", "label")

                    properties.entries.any { it.value != null && allowedKeys.contains(it.key ?: "") && candidate.extraHints.matches(it.value) } ||
                            candidate.extraHints.matches(lastString) || (currentText != "" && candidate.extraHints.matches(currentText)) ||
                            node.autofillHints.orEmpty().any { candidate.extraHints.matches(it) }
                }
            }
            Log.i("typeb", "$type")

            if (type == null) {
                val variation = InputType.TYPE_MASK_VARIATION and node.inputType
                type = when (variation) {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> AutofillType.PASSWORD

                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> AutofillType.EMAIL

                    else -> null
                }
            }

            Log.i("typec", "$type ${node.inputType}")

            if (type != null) {
                fields.add(Pair(node, type))
            }

            Log.i("FILL", "Got form $lastString $currentText ${node.contentDescription} ${node.htmlInfo?.attributes?.joinToString(" ")} ${node.autofillHints?.joinToString("|")} $type")
            lastString = ""
        }

        if (node.webDomain != null) {
            webDomain = node.webDomain?.replaceFirst("www.", "")
        }


        if (currentText != "" && node.autofillId == null) {
            lastString = currentText
        }

        for (i in 0..<node.childCount) {
            processNode(node.getChildAt(i))
        }
    }
}