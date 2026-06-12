package com.feuch.clochette

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class ClochetteNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val snapshot = NowPlayingObserver.fromNotification(this, sbn.packageName, notification) ?: return
        NowPlayingObserver.save(this, snapshot)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        val current = NowPlayingObserver.snapshot(this)
        if (current.appName == packageName) {
            NowPlayingObserver.save(this, current.copy(playing = false))
        }
    }
}
