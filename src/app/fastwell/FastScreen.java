package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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

    static final String[] MOODS = {"Rough", "Meh", "OK", "Good", "Great"};

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
            public void onClick(View v) { planDialog(a); }
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
        ringBox.addView(center, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        col.addView(ringBox, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 280), 0, 4, 0, 12));

        col.addView(timesCard(plan));

        Button main;
        if (active != null) {
            main = Ui.primary(c, "End fast", R.color.primary);
            main.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { endFastDialog(a, active); }
            });
        } else {
            main = Ui.primary(c, "Start " + plan.fastHours + "h fast", R.color.primary);
            main.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { a.startFast(); }
            });
        }
        col.addView(main, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 56), 0, 0, 0, 12));

        col.addView(stageCard());
        col.addView(recentCard(plan));
        return Ui.scroll(c, col);
    }

    private View timesCard(FastingLogic.Plan plan) {
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
            if (last != null) {
                row.addView(timeCell("Last fast", FastingLogic.dur(last.endAt - last.startAt), last.met() ? "Goal met" : "Ended early", null),
                        Ui.lp(0, Ui.WRAP, 1f));
                row.addView(timeCell("Ended", Ui.dateTime(c, last.endAt), "", null), Ui.lp(0, Ui.WRAP, 1f));
            } else {
                row.addView(timeCell("Plan", plan.name.replace("  ", " "), plan.fastHours + "h fasting, " + plan.eatHours() + "h eating", null),
                        Ui.lp(0, Ui.WRAP, 1f));
            }
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
        card.addView(Ui.muted(c, "Current stage"));
        stageName = Ui.text(c, "", 18, R.color.text, true);
        card.addView(stageName);
        stageNext = Ui.muted(c, "");
        card.addView(stageNext);
        TextView note = Ui.text(c, "General information, not medical advice.", 11, R.color.text2, false);
        note.setPadding(0, Ui.dp(c, 6), 0, 0);
        card.addView(note);
        return card;
    }

    private View recentCard(FastingLogic.Plan plan) {
        LinearLayout card = Ui.card(c);
        LinearLayout head = Ui.hbox(c);
        head.addView(Ui.text(c, "Last 7 fasts", 16, R.color.text, true), Ui.lp(0, Ui.WRAP, 1f));
        Button hist = Ui.flat(c, "History", R.color.primary);
        hist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.push(new FastHistoryScreen(a)); }
        });
        head.addView(hist, Ui.lp(Ui.WRAP, Ui.dp(c, 40)));
        card.addView(head);
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

    private void editStart() {
        final Db.Fast f = active;
        a.pickDateTime(f.startAt, new MainActivity.LongCb() {
            @Override
            public void on(long t) {
                if (t > System.currentTimeMillis()) { Ui.toast(c, "Start time can't be in the future"); return; }
                Db.Fast last = db.lastCompletedFast();
                if (last != null && t < last.endAt) { Ui.toast(c, "That overlaps your previous fast"); return; }
                f.startAt = t;
                db.updateFast(f);
                Notifier.refresh(c);
                a.refresh();
            }
        });
    }

    // ---------- shared dialogs ----------

    static void planDialog(final MainActivity a) {
        final Prefs prefs = a.prefs;
        int n = FastingLogic.PRESETS.length;
        String[] names = new String[n + 1];
        int checked = n;
        for (int i = 0; i < n; i++) {
            FastingLogic.Plan p = FastingLogic.PRESETS[i];
            names[i] = p.name + "   (" + p.fastHours + "h fast, " + p.eatHours() + "h eat)";
            if (p.id.equals(prefs.planId())) checked = i;
        }
        names[n] = "Custom length...";
        new AlertDialog.Builder(a)
                .setTitle("Fasting plan")
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which < FastingLogic.PRESETS.length) {
                            applyPlan(a, FastingLogic.PRESETS[which].id, prefs.customHours());
                        } else {
                            customHoursDialog(a);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void customHoursDialog(final MainActivity a) {
        HourDial.show(a, Math.min(HourDial.MAX, a.prefs.customHours()), new HourDial.IntCb() {
            @Override
            public void on(int h) { applyPlan(a, FastingLogic.CUSTOM, h); }
        });
    }

    private static void applyPlan(MainActivity a, String id, int customHours) {
        a.prefs.setPlan(id, customHours);
        FastingLogic.Plan p = a.prefs.plan();
        Db.Fast f = a.db.activeFast();
        if (f != null) {
            f.goalMin = p.goalMinutes();
            f.plan = p.id;
            a.db.updateFast(f);
        }
        Notifier.refresh(a);
        a.refresh();
        a.snack("Plan set to " + p.ratio(), null);
    }

    static void endFastDialog(final MainActivity a, final Db.Fast f) {
        final long[] end = {System.currentTimeMillis()};
        LinearLayout body = Ui.dialogBody(a);
        final TextView total = Ui.big(a, FastingLogic.dur(end[0] - f.startAt), 30, R.color.text);
        body.addView(total);
        final TextView goal = Ui.muted(a, "");
        body.addView(goal);
        final Button endBtn = Ui.tonal(a, "", R.color.primary);
        final Runnable update = new Runnable() {
            @Override
            public void run() {
                long d = end[0] - f.startAt;
                total.setText(FastingLogic.dur(d));
                goal.setText(d >= f.goalMs() ? "Goal of " + FastingLogic.dur(f.goalMs()) + " met"
                        : FastingLogic.dur(f.goalMs() - d) + " short of your " + FastingLogic.dur(f.goalMs()) + " goal");
                endBtn.setText("Ended " + Ui.dateTime(a, end[0]) + "  (change)");
            }
        };
        update.run();
        endBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.pickDateTime(end[0], new MainActivity.LongCb() {
                    @Override
                    public void on(long t) {
                        if (t <= f.startAt) { Ui.toast(a, "End must be after the start"); return; }
                        if (t > System.currentTimeMillis() + 60000) { Ui.toast(a, "End can't be in the future"); return; }
                        end[0] = t;
                        update.run();
                    }
                });
            }
        });
        body.addView(endBtn, Ui.lpm(a, Ui.MATCH, Ui.dp(a, 44), 0, 12, 0, 4));

        body.addView(Ui.labeled(a, "How did it feel? (optional)", new View(a)));
        final RadioGroup moods = new RadioGroup(a);
        moods.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < MOODS.length; i++) {
            RadioButton rb = new RadioButton(a);
            rb.setText(MOODS[i]);
            rb.setTextSize(12);
            rb.setId(1000 + i);
            moods.addView(rb, new RadioGroup.LayoutParams(0, Ui.WRAP, 1f));
        }
        body.addView(moods);
        final EditText note = Ui.field(a, "Note (optional)", Ui.TXT);
        body.addView(note, Ui.lp(Ui.MATCH, Ui.WRAP));

        new AlertDialog.Builder(a)
                .setTitle("End fast")
                .setView(body)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        int mood = moods.getCheckedRadioButtonId() >= 1000 ? moods.getCheckedRadioButtonId() - 999 : 0;
                        a.db.endFast(f.id, end[0], mood, note.getText().toString().trim());
                        Notifier.refresh(a);
                        a.refresh();
                        a.snack("Fast saved  -  " + FastingLogic.dur(end[0] - f.startAt), new Runnable() {
                            @Override
                            public void run() {
                                Db.Fast back = a.db.getFast(f.id);
                                if (back != null && a.db.activeFast() == null) {
                                    back.endAt = 0;
                                    back.mood = 0;
                                    back.note = "";
                                    a.db.updateFast(back);
                                }
                            }
                        });
                    }
                })
                .setNegativeButton("Keep fasting", null)
                .show();
    }
}
