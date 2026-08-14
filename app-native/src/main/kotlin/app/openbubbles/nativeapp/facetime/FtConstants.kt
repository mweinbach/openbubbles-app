package app.openbubbles.nativeapp.facetime

/** Values the ported FaceTime subsystem needs (from the Flutter app's Constants). */
object FtConstants {
    const val NEW_FACE_TIME_NOTIFICATION_TAG = "com.bluebubbles.messaging.NEW_FACETIME_NOTIFICATION"
    const val PENDING_INTENT_ANSWER_OFFSET = -100000
    const val PENDING_INTENT_DECLINE_OFFSET = -200000
    const val PENDING_INTENT_OPEN_OFFSET = -300000
}

/** Minimal port of the Flutter app's volume compat helper. */
fun android.media.AudioManager.getStreamMinVolumeCompat(streamType: Int): Int =
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        getStreamMinVolume(streamType)
    } else {
        0
    }

/** Port of the Flutter app's Utils.getAdaptiveIconFromByteArray. */
fun getAdaptiveIconFromByteArray(data: ByteArray): androidx.core.graphics.drawable.IconCompat {
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
    var width = bitmap.width
    var height = bitmap.height
    val aspectRatio = width / height
    if (aspectRatio > 1) {
        width = (72 * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
        height = width / aspectRatio
    } else {
        height = (72 * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
        width = height * aspectRatio
    }
    val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true)
    return androidx.core.graphics.drawable.IconCompat.createWithAdaptiveBitmap(scaled)
}
