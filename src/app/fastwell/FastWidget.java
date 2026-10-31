package app.fastwell;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Home-screen fasting timer. The clock is a system chronometer and ticks on its own; the ring and
 * text are redrawn on every fast change (from Notifier.refresh) and each minute of a fast, by an
 * alarm that doesn't wake the phone.
 */
public class FastWidget extends AppWidgetProvider {
    /** The ring's stage dots reach just past its bounds, so it is drawn this far in from the image edge. */
    private static final int INSET_DP = 1;
    /** The stage text below the ring, as shares of the ring's size but never too small to read. */
    private static final float STAGE_TEXT = 0.075f, STAGE_LINE_TEXT = 0.058f;
    private static final float MIN_STAGE_DP = 10, MIN_STAGE_LINE_DP = 8;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) { refresh(c); }

    @Override
    public void onDisabled(Context c) { refresh(c); }

    /** Redraws every placed widget and schedules the next redraw. Safe to call any time. */
    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent next = Notifier.receiver(c, FastReceiver.ACTION_WIDGET);
        am.cancel(next);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, FastWidget.class));
        if (ids.length == 0) return;
        Db.Fast f = Db.get(c).activeFast();
        long now = System.currentTimeMillis();
        for (int id : ids) {
            // The app is portrait only, and a portrait tile is at its narrowest and tallest.
            Bundle o = m.getAppWidgetOptions(id);
            int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
            m.updateAppWidget(id, views(c, f, now, FastingLogic.widgetRingDp(w, h), FastingLogic.widgetWide(w, h)));
        }
        if (f != null) am.set(AlarmManager.RTC, FastingLogic.nextMinute(now, f.startAt), next);
    }

    private static RemoteViews views(Context c, Db.Fast f, long now, int ringDp, boolean wide) {
        FastingLogic.Plan plan = new Prefs(c).plan();
        long goalMs = f != null ? f.goalMs() : plan.goalMinutes() * 60000L;
        FastingLogic.Status s = f != null ? FastingLogic.fasting(f.durationMs(now), goalMs)
                : FastingLogic.notFasting(plan.fastHours);
        RemoteViews v = new RemoteViews(c.getPackageName(), wide ? R.layout.widget_fast_wide : R.layout.widget_fast);
        v.setImageViewBitmap(R.id.widget_ring, ring(c.getApplicationContext(), s.progress, goalMs, ringDp));
        v.setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_DIP, FastingLogic.widgetClockDp(ringDp));
        v.setTextViewTextSize(R.id.widget_label, TypedValue.COMPLEX_UNIT_DIP, FastingLogic.widgetLineDp(ringDp));
        v.setTextViewTextSize(R.id.widget_remaining, TypedValue.COMPLEX_UNIT_DIP, FastingLogic.widgetLineDp(ringDp));
        if (!wide) {
            float line = Math.max(MIN_STAGE_LINE_DP, ringDp * STAGE_LINE_TEXT);
            v.setTextViewTextSize(R.id.widget_stage_label, TypedValue.COMPLEX_UNIT_DIP, line);
            v.setTextViewTextSize(R.id.widget_stage, TypedValue.COMPLEX_UNIT_DIP, Math.max(MIN_STAGE_DP, ringDp * STAGE_TEXT));
            v.setTextViewTextSize(R.id.widget_next, TypedValue.COMPLEX_UNIT_DIP, line);
        }
        v.setTextViewText(R.id.widget_label, s.label);
        v.setTextViewText(R.id.widget_remaining, s.remaining);
        v.setTextViewText(R.id.widget_stage, s.stage);
        v.setTextViewText(R.id.widget_next, s.next);
        if (f != null) {
            v.setChronometer(R.id.widget_clock, SystemClock.elapsedRealtime() - f.durationMs(now), null, true);
        } else {
            v.setChronometer(R.id.widget_clock, SystemClock.elapsedRealtime(), "0:00:00", false);
        }
        v.setOnClickPendingIntent(R.id.widget_root, Notifier.openApp(c, MainActivity.TAB_FAST));
        return v;
    }

    /** The Fast screen's ring, drawn off screen. */
    private static Bitmap ring(Context c, float progress, long goalMs, int ringDp) {
        Views.Ring ring = new Views.Ring(c, ringDp * FastingLogic.WIDGET_STROKE, Ui.col(c, R.color.primary));
        float[] ticks = new float[FastingLogic.STAGE_HOURS.length];
        for (int i = 0; i < ticks.length; i++) ticks[i] = FastingLogic.STAGE_HOURS[i] * 3_600_000f / goalMs;
        ring.setTicks(ticks);
        ring.setProgress(progress, false);
        int size = Ui.dp(c, ringDp), inset = Ui.dp(c, INSET_DP), inner = size - 2 * inset;
        ring.measure(View.MeasureSpec.makeMeasureSpec(inner, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(inner, View.MeasureSpec.EXACTLY));
        ring.layout(0, 0, inner, inner);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.translate(inset, inset);
        ring.draw(canvas);
        return b;
    }
}
