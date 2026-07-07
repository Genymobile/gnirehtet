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
    private static final int STATUS_NOTIFICATION_ID = 43;
    private static final String CHANNEL_ID = "Gnirehtet";

    private final Service context;
    private boolean failure;

    public Notifier(Service context) {
        this.context = context;
    }

    private Notification createNotification(boolean failure) {
        return createNotification(failure, failure ? context.getString(R.string.relay_disconnected)
                : context.getString(R.string.relay_connected));
    }

    private Notification createNotification(boolean failure, String message) {
        Notification.Builder notificationBuilder = createNotificationBuilder();
        notificationBuilder.setContentTitle(context.getString(R.string.app_name));
        notificationBuilder.setContentText(message);
        if (failure) {
            notificationBuilder.setSmallIcon(R.drawable.ic_report_problem_24dp);
        } else {
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
        // Keep the channel so disconnected status notifications can still be shown after the
        // foreground service is stopped.
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

    public void showConnected() {
        String pcName = GnirehtetSettings.getPcName(context);
        String message = pcName.length() == 0 ? context.getString(R.string.notification_connected)
                : context.getString(R.string.notification_connected_with_pc, pcName);
        getNotificationManager().notify(STATUS_NOTIFICATION_ID, createNotification(false,
                message));
    }

    public void showDisconnected() {
        getNotificationManager().notify(STATUS_NOTIFICATION_ID, createNotification(true,
                context.getString(R.string.notification_disconnected)));
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
