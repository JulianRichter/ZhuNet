package richter.julian.zhunet;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class AppWrapper extends Application {

    public static final String GRAPH_CHANNEL = "richter.julian.zhunet.graph_channel";

    @Override
    public void onCreate() {
        super.onCreate();

        createGraphNotificationChannel();

    }

    /**
     * Create the Channel for Notifications.
     * This is ignored for Versions before Oreo.
     */
    private void createGraphNotificationChannel() {

        // NotificationChannel only exist on Version Oreo (25) or higher.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel;
            NotificationManager manager;

            channel = new NotificationChannel(
                    GRAPH_CHANNEL,
                    "Graph Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );

            // Reinstall to change Channel Settings.
            channel.setDescription("This is the Graph Channel for daily notifications.");

            manager = getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);

            }

        }

    }

}
