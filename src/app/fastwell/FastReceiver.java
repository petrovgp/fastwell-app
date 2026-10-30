package app.fastwell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Goal alarm, the widget's redraw alarm and the "End fast" notification action. Not exported. */
public class FastReceiver extends BroadcastReceiver {
    static final String ACTION_GOAL = "app.fastwell.GOAL";
    static final String ACTION_END = "app.fastwell.END_FAST";
    static final String ACTION_WIDGET = "app.fastwell.WIDGET";

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (ACTION_GOAL.equals(action)) {
            Notifier.goalReached(c);
        } else if (ACTION_WIDGET.equals(action)) {
            FastWidget.refresh(c);
        } else if (ACTION_END.equals(action)) {
            Db db = Db.get(c);
            Db.Fast f = db.activeFast();
            if (f != null) {
                long now = System.currentTimeMillis();
                db.endFast(f.id, now, 0, "");
                Notifier.fastEnded(c, f, now);
            }
            Notifier.refresh(c);
            MainActivity.notifyDataChanged();
        }
    }
}
