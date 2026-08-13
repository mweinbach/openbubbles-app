package com.bluebubbles.messaging.services.system

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.bluebubbles.messaging.Constants
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.utils.Utils
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.UUID

class ZenModeUUIDHandler: MethodCallHandlerImpl() {
    companion object {
        const val tag = "zen-mode-uuid"

        fun getZenKey(context: Context, key: String): String {
            val prefs = context.getSharedPreferences("zen-mode-uuid", Context.MODE_PRIVATE)
            if (!prefs.contains(key)) {
                with (prefs.edit()) {
                    putString(key, UUID.randomUUID().toString().uppercase())
                    apply()
                }
            }
            return prefs.getString(key, null)!!
        }

        fun isZenEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("zen-mode-uuid", Context.MODE_PRIVATE)
            return prefs.getBoolean("zen_sharing", false)
        }

        fun setZenEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences("zen-mode-uuid", Context.MODE_PRIVATE)
            with (prefs.edit()) {
                putBoolean("zen_sharing", enabled)
                apply()
            }
        }

    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val name: String = call.argument("key")!!
        if (name == "enable") {
            setZenEnabled(context, true)
            result.success(null)
            return
        } else if (name == "disable") {
            setZenEnabled(context, false)
            result.success(null)
            return
        }
        result.success(getZenKey(context, name))
    }
}