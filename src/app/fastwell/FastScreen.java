package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** The fasting timer: big ring, start/end, editable times, stage info, last 7 fasts. */
final class FastScreen extends Screen {
    private Views.Ring ring;
    private TextView stateLabel, elapsed, remaining, stageName, stageNext;
    private Db.Fast active;

    FastScreen(MainActivity a) { super(a); }

    @Override
    View build() {
        LinearLayout col = Ui.column(c, 24);
        active = db.activeFast();
        final FastingLogic.Plan plan = prefs.plan();

        LinearLayout top = Ui.hbox(c);
        top.addView(Ui.header(c, "Fast", active != null ? "Fasting since " + Ui.dateTime(c, active.startAt) : "Ready when you are"),
                Ui.lp(0, Ui.WRAP, 1f));
        Button planBtn = Ui.tonal(c, "Plan " + (active != null && !active.plan.isEmpty() ? FastingLogic.plan(active.plan, (int) (active.goalMin / 60)).ratio() : plan.ratio()), R.color.primary);
        planBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.push(new PlanScreen(a)); }
        });
        top.addView(planBtn, Ui.lp(Ui.WRAP, Ui.dp(c, 40)));
        col.addView(top);

        // Ring with centered timer.
        FrameLayout ringBox = new FrameLayout(c);
        ring = new Views.Ring(c, 18, Ui.col(c, R.color.primary));
        long goalMs = active != null ? active.goalMs() : plan.goalMinutes() * 60000L;
        float[] ticks = new float[FastingLogic.STAGE_HOURS.length];
        for (int i = 0; i < ticks.length; i++) ticks[i] = FastingLogic.STAGE_HOURS[i] * 3_600_000f / goalMs;
        ring.setTicks(ticks);
        ringBox.addView(ring, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        LinearLayout center = Ui.vbox(c);
        center.setGravity(Gravity.CENTER);
        stateLabel = Ui.muted(c, "");
        stateLabel.setGravity(Gravity.CENTER);
        elapsed = Ui.big(c, "", 40, R.color.text);
        elapsed.setGravity(Gravity.CENTER);
        remaining = Ui.muted(c, "");
        remaining.setGravity(Gravity.CENTER);
        center.addView(stateLabel);
        center.addView(elapsed);
        center.addView(remaining);

        // Tapping the timer does the same as the main button below.
        View.OnClickListener toggle = active != null
                ? new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { a.push(new EndFastScreen(a, active)); }
                }
                : new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { a.startFast(); }
                };
        int pad = Ui.dp(c, 16);
        center.setPadding(pad, pad, pad, pad);
        Ui.clickable(center, 24);
        center.setOnClickListener(toggle);
        center.setContentDescription(active != null ? "End fast" : "Start " + plan.fastHours + "h fast");
        ringBox.addView(center, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        col.addView(ringBox, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 280), 0, 4, 0, 12));

        View times = timesCard();
        if (times != null) col.addView(times);

        Button main = Ui.primary(c, active != null ? "End fast" : "Start " + plan.fastHours + "h fast", R.color.primary);
        main.setOnClickListener(toggle);
        col.addView(main, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 56), 0, 0, 0, 12));

        col.addView(stageCard());
        col.addView(recentCard(plan));
        return Ui.scroll(c, col);
    }

    /** Start/goal times of the running fast, or the last completed fast; null when there is neither. */
    private View timesCard() {
        LinearLayout card = Ui.card(c);
        LinearLayout row = Ui.hbox(c);
        if (active != null) {
            row.addView(timeCell("Started", Ui.dateTime(c, active.startAt), "Tap to edit", new View.OnClickListener() {
                @Override
                public void onClick(View v) { editStart(); }
            }), Ui.lp(0, Ui.WRAP, 1f));
            row.addView(timeCell("Goal end", Ui.dateTime(c, active.startAt + active.goalMs()), FastingLogic.dur(active.goalMs()) + " goal", null),
                    Ui.lp(0, Ui.WRAP, 1f));
        } else {
            Db.Fast last = db.lastCompletedFast();
            if (last == null) return null;
            row.addView(timeCell("Last fast", FastingLogic.dur(last.endAt - last.startAt), last.met() ? "Goal met" : "Ended early", null),
                    Ui.lp(0, Ui.WRAP, 1f));
            row.addView(timeCell("Ended", Ui.dateTime(c, last.endAt), "", null), Ui.lp(0, Ui.WRAP, 1f));
        }
        card.addView(row);
        return card;
    }

    private View timeCell(String label, String value, String sub, View.OnClickListener click) {
        LinearLayout cell = Ui.vbox(c);
        cell.setPadding(Ui.dp(c, 4), Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2));
        cell.addView(Ui.muted(c, label));
        cell.addView(Ui.text(c, value, 16, R.color.text, true));
        if (!sub.isEmpty()) cell.addView(Ui.text(c, sub, 12, click != null ? R.color.primary : R.color.text2, false));
        if (click != null) {
            Ui.clickable(cell, 8);
            cell.setOnClickListener(click);
        }
        return cell;
    }

    private View stageCard() {
        LinearLayout card = Ui.card(c);
        LinearLayout head = Ui.hbox(c);
        head.addView(Ui.muted(c, "Current stage"), Ui.lp(0, Ui.WRAP, 1f));
        head.addView(Ui.text(c, "All stages", 13, R.color.primary, true));
        card.addView(head);
        stageName = Ui.text(c, "", 18, R.color.text, true);
        card.addView(stageName);
        stageNext = Ui.muted(c, "");
        card.addView(stageNext);
        TextView note = Ui.text(c, "General information, not medical advice.", 11, R.color.text2, false);
        note.setPadding(0, Ui.dp(c, 6), 0, 0);
        card.addView(note);
        Ui.clickable(card, 16);
        card.setContentDescription("Current stage. Show all fasting stages");
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.push(new StagesScreen(a)); }
        });
        return card;
    }

    private View recentCard(FastingLogic.Plan plan) {
        LinearLayout card = Ui.card(c);
        LinearLayout head = Ui.hbox(c);
        head.addView(Ui.text(c, "Last 7 fasts", 16, R.color.text, true), Ui.lp(0, Ui.WRAP, 1f));
        head.addView(Ui.text(c, "History", 13, R.color.primary, true));
        card.addView(head);
        Ui.clickable(card, 16);
        card.setContentDescription("Last 7 fasts. Show fasting history");
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.push(new FastHistoryScreen(a)); }
        });
        List<Db.Fast> fasts = db.completedFasts(7);
        if (fasts.isEmpty()) {
            card.addView(Ui.muted(c, "Your completed fasts will show here."));
            return card;
        }
        int n = fasts.size();
        float[] v = new float[n];
        String[] l = new String[n];
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE", Locale.getDefault());
        for (int i = 0; i < n; i++) {
            Db.Fast fa = fasts.get(n - 1 - i);
            v[i] = (fa.endAt - fa.startAt) / 3_600_000f;
            l[i] = Instant.ofEpochMilli(fa.endAt).atZone(ZoneId.systemDefault()).format(f);
        }
        Views.Chart chart = new Views.Chart(c, Ui.col(c, R.color.primary));
        chart.setData(v, l, plan.fastHours, true);
        card.addView(chart, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 140), 0, 8, 0, 0));
        return card;
    }

    @Override
    void tick() {
        if (ring == null) return;
        long now = System.currentTimeMillis();
        if (active == null) {
            ring.setProgress(0, false);
            stateLabel.setText("Not fasting");
            elapsed.setText("0:00:00");
            remaining.setText("Goal " + prefs.plan().fastHours + " hours");
            stageName.setText("Eating window");
            stageNext.setText("Start a fast after your last meal of the day.");
            return;
        }
        long d = active.durationMs(now);
        float p = d / (float) active.goalMs();
        ring.setProgress(p, true);
        stateLabel.setText(p >= 1 ? "Goal reached" : "Elapsed  -  " + Math.min(100, (int) (p * 100)) + "%");
        elapsed.setText(FastingLogic.hms(d));
        remaining.setText(p >= 1 ? "+" + FastingLogic.dur(d - active.goalMs()) + " over goal"
                : FastingLogic.dur(active.goalMs() - d) + " to go");
        double hours = d / 3_600_000.0;
        int idx = FastingLogic.stageIndex(hours);
        stageName.setText(FastingLogic.STAGE_NAMES[idx]);
        if (idx + 1 < FastingLogic.STAGE_HOURS.length) {
            long nextAt = FastingLogic.STAGE_HOURS[idx + 1] * 3_600_000L;
            stageNext.setText("Next: " + FastingLogic.STAGE_NAMES[idx + 1] + " in " + FastingLogic.dur(nextAt - d));
        } else {
            stageNext.setText("Fasts this long should be medically supervised.");
        }
    }

    private void editStart() { a.push(new EditStartScreen(a, active)); }
}
