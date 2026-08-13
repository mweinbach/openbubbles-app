package com.bluebubbles.messaging.services.rustpush

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.bluebubbles.messaging.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.concurrent.thread

class AuthCodeActivity : Activity() {

    var dialog: AlertDialog? = null
    var done = false
    var dialogView: View? = null

    override fun onDestroy() {
        super.onDestroy()
        dialog?.cancel()
        AppleAccountLoginHandler.activity = null
    }


    private fun shakeView(view: View) {
        val springAnim = SpringAnimation(view, DynamicAnimation.TRANSLATION_X, 0f).apply {
            spring = SpringForce(0f).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM   // bounciness
                dampingRatio = SpringForce.DAMPING_RATIO_HIGH_BOUNCY // how quickly it settles
            }
            setStartVelocity(3000f) // initial velocity for the shake
        }
        springAnim.start()
    }

    fun handleLoginSuccess(success: Boolean) {
        runOnUiThread {
            if (success) {
                finishAndRemoveTask()
            } else {
                dialogView?.findViewById<TextView>(R.id.text_center_big)?.let {
                    shakeView(it)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppleAccountLoginHandler.activity = this


        dialog = MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(AppleAccountLoginHandler.title!!)
            .setMessage(AppleAccountLoginHandler.body!!)
            .setNegativeButton(AppleAccountLoginHandler.albtn) { dialog, which ->
                val client = APNClient(applicationContext)
                client.bind { service: APNService ->
                    try {
                        service.pushState?.teardown2fa("albtn", AppleAccountLoginHandler.txnid!!)
                        AppleAccountLoginHandler.txnid = null
                    } finally {
                        client.destroy()
                    }
                }
                finishAndRemoveTask()
            }
            .setPositiveButton(AppleAccountLoginHandler.defbtn) { dialog, which ->
                thread {
                    val client = APNClient(applicationContext)
                    client.bind { service: APNService ->
                        try {
                            done = true
                            // will block, get before tearing down because that can cause GSA to lock
                            val code = service.pushState!!.getAuthCode(AppleAccountLoginHandler.txnid!!)
                            service.pushState?.teardown2fa("defbtn", AppleAccountLoginHandler.txnid!!)
                            AppleAccountLoginHandler.txnid = null
                            runOnUiThread {
                                allow(code)
                            }
                        } finally {
                            client.destroy()
                        }
                    }
                }
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    fun allow(code: UInt) {
        val body = LayoutInflater.from(this).inflate(R.layout.twofa_dialog_body, null)

        body.findViewById<TextView>(R.id.text_small_top).text = AppleAccountLoginHandler.sbdy

        body.findViewById<TextView>(R.id.text_center_big).text = code.toString().padStart(6, '0')

        if (AppleAccountLoginHandler.sbdy!!.contains("Apple will never")) {
            body.findViewById<TextView>(R.id.text_small_bottom).visibility = View.GONE
        }

        dialogView = body

        dialog = MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle("Apple Account Verification code")
            .setView(body)
            .setPositiveButton("Ok") { dialog, which ->
                dialog.dismiss()
                finishAndRemoveTask()
            }
            .setCancelable(false)
            .show()

    }
}