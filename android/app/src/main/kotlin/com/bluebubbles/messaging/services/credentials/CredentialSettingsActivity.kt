package com.bluebubbles.messaging.services.credentials

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bluebubbles.messaging.MainActivity

class CredentialSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("chatGuid", "-55")
        startActivity(intent)
        finish()
    }
}