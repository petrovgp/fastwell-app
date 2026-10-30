package app.fastwell;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Dashboard: fast status, calories, water and training vs target, sized to fit one screen. */
final class TodayScreen extends Screen {
    private Views.Ring ring;
    private TextView fastTime, fastSub, ringPct;
    private Db.Fast active;

    TodayScreen(MainActivity a) { super(a); }

    @Override
    View build() {
        LinearLayout col = Ui.column(c, 0);
        active = db.activeFast();

        LinearLayout top = Ui.hbox(c);
        int hour = LocalTime.now().getHour();
        String greet = hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        LinearLayout header = Ui.header(c, "Today",
                greet + "  -  " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())));
        header.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 8));
        top.addView(header, Ui.lp(0, Ui.WRAP, 1f));
        ImageView settings = Ui.iconButton(c, R.drawable.ic_settings, R.color.text2, "Settings");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.push(new SettingsScreen(a)); }
        });
        top.addView(settings);
        col.addView(top);

        col.addView(fastCard());
        col.addView(summaryCard());
        col.addView(trainingCard());
        // Still a ScrollView so a tiny screen or huge font scrolls rather than clipping; otherwise it all fits.
        return Ui.scroll(c, col);
    }

    /** Tightens a card and has it share the spare screen height, by weight, with its content centered. */
    private void fit(LinearLayout card, float weight) {
        int pad = Ui.dp(c, 12);
        card.setPadding(pad, pad, pad, pad);
        card.setGravity(Gravity.CENTER_VERTICAL);
        ((LinearLayout.LayoutParams) card.getLayoutParams()).weight = weight;
    }

    private View fastCard() {
        LinearLayout card = Ui.card(c);
        LinearLayout row = Ui.hbox(c);
        FrameLayout ringBox = new FrameLayout(c);
        ring = new Views.Ring(c, 9, Ui.col(c, active != null ? R.color.primary : R.color.eat));
        ringBox.addView(ring, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        ringPct = Ui.text(c, "", 14, R.color.text, true);
        ringPct.setGravity(Gravity.CENTER);
        ringBox.addView(ringPct, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        row.addView(ringBox, Ui.lp(Ui.dp(c, 72), Ui.dp(c, 72)));
        row.addView(Ui.hspace(c, 16));

        LinearLayout info = Ui.vbox(c);
        LinearLayout chipRow = Ui.hbox(c);
        chipRow.addView(Ui.chip(c, active != null ? "Fasting" : "Eating window", active != null ? R.color.primary : R.color.eat));
        info.addView(chipRow);
        fastTime = Ui.big(c, "", 28, R.color.text);
        fastTime.setPadding(0, Ui.dp(c, 4), 0, 0);
        info.addView(fastTime);
        fastSub = Ui.muted(c, "");
        info.addView(fastSub);
        row.addView(info, Ui.lp(0, Ui.WRAP, 1f));
        card.addView(row);
        fit(card, 1f);
        Ui.clickable(card, 16);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.showTab(MainActivity.TAB_FAST); }
        });
        return card;
    }

    @Override
    void tick() {
        if (ring == null) return;
        long now = System.currentTimeMillis();
        if (active != null) {
            float p = active.durationMs(now) / (float) active.goalMs();
            ring.setProgress(p, true);
            ringPct.setText(Math.min(999, (int) (p * 100)) + "%");
            fastTime.setText(FastingLogic.dur(active.durationMs(now)));
            long goalAt = active.startAt + active.goalMs();
            fastSub.setText(now >= goalAt ? "Goal of " + FastingLogic.dur(active.goalMs()) + " reached"
                    : "of " + FastingLogic.dur(active.goalMs()) + "  -  ends " + Ui.time(c, goalAt));
        } else {
            Db.Fast last = db.lastCompletedFast();
            ring.setProgress(0, false);
            ringPct.setText("");
            if (last == null) {
                fastTime.setText("Not fasting");
                fastSub.setText("Plan " + prefs.plan().ratio() + "  -  start when you finish eating");
            } else {
                fastTime.setText(FastingLogic.dur(now - last.endAt));
                fastSub.setText("since your last fast (" + FastingLogic.dur(last.endAt - last.startAt) + ")");
            }
        }
    }

    private View summaryCard() {
        LinearLayout card = summaryCard(a, Db.today(), false);
        fit(card, 2f);
        Ui.clickable(card, 16);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.selectedDay = LocalDate.now();
                a.showTab(MainActivity.TAB_FOOD);
            }
        });
        return card;
    }

    /**
     * A day's calories, macros and water against their targets, raised by that day's workouts; shown on both Today
     * and Food. fullNote spells the raise out on its own line; otherwise it's a short tag that adds no height.
     */
    static LinearLayout summaryCard(MainActivity a, String day, boolean fullNote) {
        double[] t = a.db.totalsForDay(day);
        double water = 0;
        for (Db.Food f : a.db.foodForDay(day)) if (f.meal == FastingLogic.WATER) water += FastingLogic.millilitres(f.amount);

        double burned = 0;
        for (Db.Workout w : a.db.workoutsForDay(day)) burned += w.kcal;
        int kcal = a.prefs.kcalTarget();

        LinearLayout card = Ui.card(a);
        String tag = burned > 0 && !fullNote ? "+" + Ui.n0(burned) + " workouts" : null;
        card.addView(total(a, R.drawable.ic_food, t[0], FastingLogic.raisedTarget(kcal, kcal, burned), " kcal", R.color.eat, true, tag),
                Ui.lpm(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, burned > 0 && fullNote ? 6 : 12));
        if (burned > 0 && fullNote) {
            card.addView(Ui.muted(a, "Includes " + Ui.n0(burned) + " kcal from workouts"), Ui.lpm(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        }
        card.addView(macroRow(a, "Protein", t[1], FastingLogic.raisedTarget(a.prefs.proteinTarget(), kcal, burned), R.color.protein));
        card.addView(macroRow(a, "Carbs", t[2], FastingLogic.raisedTarget(a.prefs.carbsTarget(), kcal, burned), R.color.carbs));
        card.addView(macroRow(a, "Fat", t[3], FastingLogic.raisedTarget(a.prefs.fatTarget(), kcal, burned), R.color.fat));
        card.addView(total(a, R.drawable.ic_stage_drop, water, a.prefs.waterTarget(), " ml", R.color.water, false, null),
                Ui.lpm(a, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        return card;
    }

    /** A day's workout count against its target, or just "Rest day" on a rest day; shown on both Today and Workouts. */
    static View workoutTotal(MainActivity a, LocalDate day, int done) {
        if (FastingLogic.isRestDay(a.prefs.restDays(), day)) {
            LinearLayout rest = Ui.hbox(a);
            rest.setGravity(Gravity.CENTER_VERTICAL);
            rest.addView(Ui.icon(a, R.drawable.ic_workout, R.color.train, 24), Ui.lpm(a, Ui.dp(a, 24), Ui.dp(a, 24), 0, 0, 8, 0));
            rest.addView(Ui.text(a, "Rest day", 16, R.color.text, true));
            return rest;
        }
        int target = a.prefs.workoutTarget();
        return total(a, R.drawable.ic_workout, done, target, FastingLogic.sessions(target), R.color.train, false, null);
    }

    /**
     * An icon and big total against its target with what's left, over a thick bar; overIsBad turns going over red.
     * A tag sits under the target, where two small lines are no taller than the big total.
     */
    private static View total(MainActivity c, int iconRes, double value, int target, String unit, int colorRes, boolean overIsBad, String tag) {
        boolean bad = overIsBad && value > target;
        LinearLayout box = Ui.vbox(c);
        LinearLayout big = Ui.hbox(c);
        big.setGravity(Gravity.BOTTOM);
        big.addView(Ui.icon(c, iconRes, colorRes, 24), Ui.lpm(c, Ui.dp(c, 24), Ui.dp(c, 24), 0, 0, 8, 7));
        big.addView(Ui.big(c, Ui.n0(value), 32, R.color.text));
        LinearLayout ofBox = Ui.vbox(c);
        ofBox.setPadding(0, 0, 0, Ui.dp(c, 6));
        ofBox.addView(Ui.muted(c, "  / " + Ui.n0(target) + unit));
        if (tag != null) {
            TextView t = Ui.text(c, "  " + tag, 13, R.color.train, false);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            ofBox.addView(t);
        }
        big.addView(ofBox, Ui.lp(0, Ui.WRAP, 1f));
        double left = target - value;
        TextView leftT = Ui.text(c, left >= 0 ? Ui.n0(left) + " left" : Ui.n0(-left) + " over", 14,
                bad ? R.color.error : R.color.text2, true);
        leftT.setPadding(0, 0, 0, Ui.dp(c, 6));
        big.addView(leftT);
        box.addView(big);
        box.addView(new Views.Bar(c, Ui.col(c, bad ? R.color.error : colorRes), (float) (value / target)),
                Ui.lpm(c, Ui.MATCH, Ui.dp(c, 10), 0, 8, 0, 0));
        return box;
    }

    private static View macroRow(MainActivity c, String name, double value, int target, int colorRes) {
        LinearLayout row = Ui.hbox(c);
        row.setPadding(0, Ui.dp(c, 3), 0, Ui.dp(c, 3));
        TextView n = Ui.text(c, name, 13, R.color.text2, false);
        row.addView(n, Ui.lp(Ui.dp(c, 64), Ui.WRAP));
        Views.Bar b = new Views.Bar(c, Ui.col(c, colorRes), target > 0 ? (float) (value / target) : 0);
        row.addView(b, Ui.lp(0, Ui.dp(c, 6), 1f));
        TextView v = Ui.text(c, Ui.n0(value) + " / " + target + " g", 13, R.color.text, false);
        v.setGravity(Gravity.END);
        row.addView(v, Ui.lp(Ui.dp(c, 96), Ui.WRAP));
        return row;
    }

    private View trainingCard() {
        LinearLayout card = Ui.card(c);
        List<Db.Workout> ws = db.workoutsForDay(Db.today());
        card.addView(workoutTotal(a, LocalDate.now(), ws.size()));
        if (!ws.isEmpty()) {
            // Only the latest, so a busy day can't push the screen past one page.
            card.addView(WorkoutsScreen.summaryRow(a, ws.get(ws.size() - 1), false));
            if (ws.size() > 1) {
                TextView more = Ui.muted(c, "+ " + (ws.size() - 1) + " more");
                more.setPadding(0, Ui.dp(c, 4), 0, 0);
                card.addView(more);
            }
        } else if (!FastingLogic.isRestDay(prefs.restDays(), LocalDate.now())) {
            TextView t = Ui.muted(c, "No workouts logged yet.");
            t.setPadding(0, Ui.dp(c, 6), 0, 0);
            card.addView(t);
        }
        fit(card, 1f);
        Ui.clickable(card, 16);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.selectedDay = LocalDate.now();
                a.showTab(MainActivity.TAB_WORKOUTS);
            }
        });
        return card;
    }
}
