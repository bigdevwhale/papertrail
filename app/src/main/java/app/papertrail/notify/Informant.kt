package app.papertrail.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.papertrail.container
import app.papertrail.domain.Notice

/**
 * Listens to the phone's notifications for bank messages. Apps the detective marked as banks become
 * envelopes on the desk; any other app that sent something money-like is only remembered by name,
 * so settings can offer it as a bank. Nothing is sent anywhere.
 */
class Informant : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (text.isBlank()) return
        val c = applicationContext.container
        c.write {
            val st = settings.now()
            if (sbn.packageName in st.banks) {
                repo.notice(sbn.packageName, title, text) ?: return@write
                Notify.cases(app, repo.investigate(st.plan))
            } else if (Notice.read(title, text) != null && sbn.packageName !in st.heard) {
                settings.update { it.copy(heard = (it.heard + sbn.packageName).toList().takeLast(20).toSet()) }
            }
        }
    }

    companion object {
        fun granted(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, Informant::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
