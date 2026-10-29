package app.fastwell;

import android.app.AlertDialog;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Build;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/** Saves the stack trace of a crash so it can be shown and copied on the next launch. */
public class CrashLog extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        final File file = file(this);
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    pw.println("Fastwell " + versionName() + " on Android " + Build.VERSION.RELEASE
                            + " (API " + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL);
                    pw.println("Thread: " + t.getName());
                    e.printStackTrace(pw);
                    pw.flush();
                    FileOutputStream os = new FileOutputStream(file);
                    os.write(sw.toString().getBytes(StandardCharsets.UTF_8));
                    os.close();
                } catch (Throwable ignored) {
                    // never let the reporter hide the original crash
                }
                if (previous != null) previous.uncaughtException(t, e);
            }
        });
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static File file(Context c) { return new File(c.getFilesDir(), "last_crash.txt"); }

    /** Shows the saved report once, with a Copy button. Returns true if a dialog was shown. */
    static boolean showIfAny(final Context c) {
        final File f = file(c);
        if (!f.exists()) return false;
        final String text;
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) Math.min(f.length(), 64 * 1024)];
            int n = in.read(buf);
            in.close();
            text = new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8);
        } catch (Exception e) {
            f.delete();
            return false;
        }
        f.delete();
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        int p = Ui.dp(c, 16);
        tv.setPadding(p, p / 2, p, p / 2);
        ScrollView sv = new ScrollView(c);
        sv.addView(tv);
        new AlertDialog.Builder(c)
                .setTitle("Fastwell closed unexpectedly")
                .setMessage("Copy this report and send it so the problem can be fixed.")
                .setView(sv)
                .setPositiveButton("Copy report", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        ClipboardManager cm = c.getSystemService(ClipboardManager.class);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Fastwell crash report", text));
                        Ui.toast(c, "Report copied");
                    }
                })
                .setNegativeButton("Close", null)
                .show();
        return true;
    }
}
