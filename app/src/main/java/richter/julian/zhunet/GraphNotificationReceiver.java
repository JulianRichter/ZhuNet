package richter.julian.zhunet;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.DisplayMetrics;

public class GraphNotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent_received) {

        // Add Extra to intent_received to choose what should be displayed?
        showNotification(context);

    }

    private void showNotification(Context context) {
        Intent intent_action;
        PendingIntent pending_intent_action;
        NotificationManagerCompat manager;
        Notification notification;
        Bitmap large_icon;

        // Open GraphActivity when Notification is clicked.
        intent_action = new Intent(context, GraphActivity.class);

        // Wrap Intent into a Pending Intent for the Notification Manager.
        pending_intent_action = PendingIntent.getActivity(
                context,
                0,
                intent_action,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Notification Manager:
        manager = NotificationManagerCompat.from(context);

        // Large Icon to be displayed:
        large_icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_graph_notification_large_icon);

        // Notification to be displayed:
        notification = new NotificationCompat.Builder(context, AppWrapper.GRAPH_CHANNEL)
                .setSmallIcon(R.drawable.ic_graph_notification_small_icon)
                .setContentTitle("Remind Me!")
                .setContentText("Did you enter your temperature today?")
                .setLargeIcon(large_icon)
                .setColor(Color.GREEN)
                .setContentIntent(pending_intent_action)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build();

        manager.notify(1, notification);

    }

    /**
     * Test to resize large Icon.
     *
     * @param context Context of the Activity.
     * @return large_icon           Bitmap of the resized large_icon.
     */
    private Bitmap resizeLargeIcon(Context context) {
        float multiplier;
        DisplayMetrics metrics;
        Bitmap large_icon;

        // Big Icon to be displayed: (just use this line? other not working)
        large_icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_graph_notification_large_icon);

        metrics = context.getResources().getDisplayMetrics();
        multiplier = metrics.density / 3f;

        large_icon = Bitmap.createScaledBitmap(
                large_icon,
                (int) (large_icon.getWidth() * multiplier),
                (int) (large_icon.getHeight() * multiplier),
                false);

        return large_icon;

    }

}
