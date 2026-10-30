package app.fastwell;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;

import java.util.Date;

/**
 * Fasting notifications. The timer itself is just a stored start time; the ongoing notification
 * uses the system chronometer, so nothing needs to run in the background.
 */
final class Notifier {
    private Notifier() {}

    static final String CH_ONGOING = "fast_ongoing";
    static final String CH_REMINDERS = "reminders";
    static final int ID_ONGOING = 1;
    static final int ID_GOAL = 2;
    static final int ID_ENDED = 3;

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ongoing = new NotificationChannel(CH_ONGOING, "Fasting timer", NotificationManager.IMPORTANCE_LOW);
        ongoing.setDescription("Shows your running fast with a live timer");
        ongoing.setShowBadge(false);
        NotificationChannel rem = new NotificationChannel(CH_REMINDERS, "Fasting reminders", NotificationManager.IMPORTANCE_HIGH);
        rem.setDescription("Tells you when you reach your fasting goal");
        nm.createNotificationChannel(ongoing);
        nm.createNotificationChannel(rem);
    }

    static PendingIntent openApp(Context c, int tab) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(MainActivity.EXTRA_TAB, tab);
        return PendingIntent.getActivity(c, 100 + tab, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent receiver(Context c, String action) {
        Intent i = new Intent(c, FastReceiver.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(c, action.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Re-syncs the widget, the ongoing notification and the goal alarm with the database. Safe to call any time. */
    static void refresh(Context c) {
        FastWidget.refresh(c);
        ensureChannels(c);
        Prefs prefs = new Prefs(c);
        Db.Fast f = Db.get(c).activeFast();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent goalPi = receiver(c, FastReceiver.ACTION_GOAL);
        am.cancel(goalPi);

        if (f == null) {
            nm.cancel(ID_ONGOING);
            return;
        }
        long goalAt = f.startAt + f.goalMs();
        long now = System.currentTimeMillis();

        if (prefs.ongoingNotif()) {
            String goalText = now >= goalAt
                    ? "Goal reached. End your fast whenever you're ready."
                    : "Goal: " + FastingLogic.dur(f.goalMs()) + " - ends " + DateFormat.getTimeFormat(c).format(new Date(goalAt));
            Notification n = new Notification.Builder(c, CH_ONGOING)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("Fasting")
                    .setContentText(goalText)
                    .setUsesChronometer(true)
                    .setShowWhen(true)
                    .setWhen(f.startAt)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                    .setColor(0xFF2E5BFF)
                    .setContentIntent(openApp(c, MainActivity.TAB_FAST))
                    .addAction(new Notification.Action.Builder(null, "End fast", receiver(c, FastReceiver.ACTION_END)).build())
                    .build();
            safeNotify(nm, ID_ONGOING, n);
        } else {
            nm.cancel(ID_ONGOING);
        }

        // Set even with the reminder off: the widget switches to goal reached on this alarm.
        if (goalAt > now) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, goalAt, goalPi);
        }
    }

    static void goalReached(Context c) {
        Db.Fast f = Db.get(c).activeFast();
        if (f == null) return;
        if (new Prefs(c).goalNotif()) {
            Notification n = new Notification.Builder(c, CH_REMINDERS)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(FastingLogic.dur(f.goalMs()) + " done")
                    .setContentText("You reached your fasting goal. Break your fast whenever you're ready.")
                    .setAutoCancel(true)
                    .setColor(0xFF1FA971)
                    .setContentIntent(openApp(c, MainActivity.TAB_FAST))
                    .build();
            safeNotify(c.getSystemService(NotificationManager.class), ID_GOAL, n);
        }
        refresh(c);
    }

    static void fastEnded(Context c, Db.Fast f, long endAt) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.cancel(ID_GOAL);
        Notification n = new Notification.Builder(c, CH_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Fast ended at " + FastingLogic.dur(endAt - f.startAt))
                .setContentText(endAt - f.startAt >= f.goalMs() ? "Goal met. Nice work." : "Saved. Every hour counts.")
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .setContentIntent(openApp(c, MainActivity.TAB_FAST))
                .build();
        safeNotify(nm, ID_ENDED, n);
    }

    private static void safeNotify(NotificationManager nm, int id, Notification n) {
        try {
            nm.notify(id, n);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted; the in-app timer still works.
        }
    }
}
