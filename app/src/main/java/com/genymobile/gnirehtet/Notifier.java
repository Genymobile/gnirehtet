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
    private boolean failure;

    public Notifier(Service context) {
        this.context = context;
    }

    private Notification createNotification(boolean failure) {
        Notification.Builder notificationBuilder = createNotificationBuilder();
        notificationBuilder.setContentTitle(context.getString(R.string.app_name));
        if (failure) {
            notificationBuilder.setContentText(context.getString(R.string.relay_disconnected));
            notificationBuilder.setSmallIcon(R.drawable.ic_report_problem_24dp);
        } else {
            notificationBuilder.setContentText(context.getString(R.string.relay_connected));
            notificationBuilder.setSmallIcon(R.drawable.ic_usb_24dp);
        }
        notificationBuilder.addAction(createStopAction());
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
        failure = false; // reset failure flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
        context.startForeground(NOTIFICATION_ID, createNotification(false));
    }

    public void stop() {
        context.stopForeground(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deleteNotificationChannel();
        }
    }

    public void setFailure(boolean failure) {
        if (this.failure != failure) {
            this.failure = failure;
            Notification notification = createNotification(failure);
            getNotificationManager().notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification.Action createStopAction() {
        Intent stopIntent = GnirehtetService.createStopIntent(context);
        PendingIntent stopPendingIntent = PendingIntent.getService(context, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT);
        // the non-deprecated constructor is not available in API 21
        @SuppressWarnings("deprecation")
        Notification.Action.Builder actionBuilder = new Notification.Action.Builder(R.drawable.ic_close_24dp, context.getString(R.string.stop_vpn),
                stopPendingIntent);
        return actionBuilder.build();
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
