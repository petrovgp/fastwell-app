package app.fastwell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms and notifications are cleared on reboot and app update; restore them. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Notifier.refresh(c);
        }
    }
}
