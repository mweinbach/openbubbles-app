package app.openbubbles.nativeapp.credentials

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity


class CredentialSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, app.openbubbles.nativeapp.NativeMainActivity::class.java)
        intent.putExtra("chatGuid", "-55")
        startActivity(intent)
        finish()
    }
}