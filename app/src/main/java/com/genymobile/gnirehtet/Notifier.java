package com.genymobile.gnirehtet;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Manage the notification necessary for the foreground service (mandatory since Android O).
 */
public class Notifier {

    private static final int NOTIFICATION_ID = 42;
    private static final String CHANNEL_ID = "Gnirehtet";

    private final Service context;

    public Notifier(Service context) {
        this.context = context;
    }

    private Notification createNotification() {
        Notification.Builder notificationBuilder = createNotificationBuilder();
        notificationBuilder.setContentText(context.getString(R.string.relay_connected));
        notificationBuilder.setSmallIcon(R.drawable.ic_usb_24dp);
        return notificationBuilder.build();
    }

    @SuppressWarnings("deprecation")
    private Notification.Builder createNotificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(context, CHANNEL_ID);
        }
        return new Notification.Builder(context);
    }

    @TargetApi(26)
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.app_name), NotificationManager
                .IMPORTANCE_DEFAULT);
        getNotificationManager().createNotificationChannel(channel);
    }

    @TargetApi(26)
    private void deleteNotificationChannel() {
        getNotificationManager().deleteNotificationChannel(CHANNEL_ID);
    }

    public void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
        context.startForeground(NOTIFICATION_ID, createNotification());
    }

    public void stop() {
        context.stopForeground(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deleteNotificationChannel();
        }
    }

    public void setFailure() {
            Notification notification = createNotification();
            getNotificationManager().notify(NOTIFICATION_ID, notification);
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
