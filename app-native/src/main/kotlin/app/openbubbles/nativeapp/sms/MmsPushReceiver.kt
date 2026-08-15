package app.openbubbles.nativeapp.sms

import android.content.Context
import android.net.Uri
import android.util.Log
import com.android.mms.transaction.PushReceiver
import com.klinker.android.send_message.MmsReceivedReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Default-SMS-role receiver that downloads carrier MMS notification PDUs. */
class MmsPushReceiver : PushReceiver()

/** Receives the carrier download completion after the PDU enters the provider. */
class CarrierMmsReceivedReceiver : MmsReceivedReceiver() {
    override fun onMessageReceived(context: Context, messageUri: Uri) {
        SmsBridge.scope.launch(Dispatchers.IO) {
            runCatching { MmsReceiver().ingestProviderMms(context, messageUri) }
                .onFailure { Log.w(TAG, "Downloaded MMS ingest failed", it) }
        }
    }

    override fun onError(context: Context, error: String) {
        Log.w(TAG, "Carrier MMS download failed: $error")
    }

    private companion object {
        private const val TAG = "CarrierMmsReceiver"
    }
}
