package com.bluebubbles.messaging.services.extension

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.bluebubbles.messaging.IMadridExtension

class MadridExtensionConnection(val bound: (ext: MadridExtensionConnection) -> Unit) : ServiceConnection {
    var extension: IMadridExtension? = null

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        extension = IMadridExtension.Stub.asInterface(service)
        bound(this)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        extension = null
    }

    fun unbind(context: Context) {
        context.unbindService(this)
    }

    companion object {
        @SuppressLint("InlinedApi")
        fun bind(appId: Int, context: Context, bound: (ext: MadridExtensionConnection) -> Unit): MadridExtensionConnection {
            val connection = MadridExtensionConnection(bound)
            val app = ExtensionRegistry.map[appId]!!
            if (!app.validateService(context)) {
                throw Exception("Bad service!")
            }
            val intent = Intent()
            intent.component = app.getServiceName()
            intent.action = IMadridExtension::class.java.name
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_ALLOW_ACTIVITY_STARTS)
            return connection
        }
    }
}