package com.genymobile.gnirehtet

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Manage the notification necessary for the foreground service (mandatory since Android O).
 */
class Notifier(private val context: Service) {
    companion object {
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "Gnirehtet"
    }

    private var failure = false

    private fun createNotification(failure: Boolean): Notification {
        val notificationBuilder = createNotificationBuilder()
        notificationBuilder.setContentTitle(context.getString(R.string.app_name))
        if (failure) {
            notificationBuilder.setContentText(context.getString(R.string.relay_disconnected))
            notificationBuilder.setSmallIcon(R.drawable.ic_report_problem_24dp)
        } else {
            notificationBuilder.setContentText(context.getString(R.string.relay_connected))
            notificationBuilder.setSmallIcon(R.drawable.ic_usb_24dp)
        }
        notificationBuilder.addAction(createStopAction())
        return notificationBuilder.build()
    }

    private fun createNotificationBuilder(): Notification.Builder {
        return Notification.Builder(context, CHANNEL_ID)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.app_name), NotificationManager
                .IMPORTANCE_DEFAULT
        )
        this.notificationManager!!.createNotificationChannel(channel)
    }

    private fun deleteNotificationChannel() {
        this.notificationManager!!.deleteNotificationChannel(CHANNEL_ID)
    }

    fun start() {
        failure = false // reset failure flag
        createNotificationChannel()
        context.startForeground(NOTIFICATION_ID, createNotification(false))
    }

    fun stop() {
        context.stopForeground(true)
        deleteNotificationChannel()
    }

    fun setFailure(failure: Boolean) {
        if (this.failure != failure) {
            this.failure = failure
            val notification = createNotification(failure)
            this.notificationManager!!.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createStopAction(): Notification.Action {
        val stopIntent: Intent = GnirehtetService.createStopIntent(context)
        val stopPendingIntent =
            PendingIntent.getService(context, 0, stopIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        // the non-deprecated constructor is not available in API 21
        @Suppress("deprecation") val actionBuilder = Notification.Action.Builder(
            R.drawable.ic_close_24dp, context.getString(R.string.stop_vpn),
            stopPendingIntent
        )
        return actionBuilder.build()
    }

    private val notificationManager: NotificationManager?
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
}