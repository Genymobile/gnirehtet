package com.genymobile.gnirehtet;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Show and hide the notification indicating a connection problem with the relay server.
 */
public class DisconnectionNotifier {

    private final Context context;

    public DisconnectionNotifier(Context context) {
        this.context = context;
    }

    public void showNotification() {
        Notification.Builder notificationBuilder = new Notification.Builder(context);
        notificationBuilder.setContentTitle(context.getString(R.string.app_name));
        notificationBuilder.setContentText(context.getString(R.string.relay_disconnected));
        notificationBuilder.setSmallIcon(R.drawable.ic_report_problem_black_24dp);
        notificationBuilder.addAction(createStopAction());
        Notification notification = notificationBuilder.build();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, notification);
    }

    public void cancelNotification() {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(0);
    }

    private Notification.Action createStopAction() {
        Intent stopIntent = GnirehtetService.createStopIntent(context);
        PendingIntent stopPendingIntent = PendingIntent.getService(context, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT);
        // the non-deprecated constructor is not available in API 21
        @SuppressWarnings("deprecation")
        Notification.Action.Builder actionBuilder =
                new Notification.Action.Builder(R.drawable.ic_close_black_24dp, context.getString(R.string.stop_vpn), stopPendingIntent);
        return actionBuilder.build();
    }
}
