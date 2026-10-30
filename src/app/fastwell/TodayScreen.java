package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Dashboard: fast status, calories and water vs target, today's training, and one state-aware action. */
final class TodayScreen extends Screen {
    private Views.Ring ring;
    private TextView fastTime, fastSub, ringPct;
    private Db.Fast active;

    TodayScreen(MainActivity a) { super(a); }

    @Override
    View build() {
        LinearLayout col = Ui.column(c, 24);
        active = db.activeFast();

        LinearLayout top = Ui.hbox(c);
        int hour = LocalTime.now().getHour();
        String greet = hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        LinearLayout header = Ui.header(c, "Today",
                greet + "  -  " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())));
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
        col.addView(actions());
        return Ui.scroll(c, col);
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
        row.addView(ringBox, Ui.lp(Ui.dp(c, 88), Ui.dp(c, 88)));
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
        LinearLayout card = summaryCard(a, Db.today());
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

    /** A day's calories, macros and water against their targets; shown on both Today and Food. */
    static LinearLayout summaryCard(MainActivity a, String day) {
        double[] t = a.db.totalsForDay(day);
        double water = 0;
        for (Db.Food f : a.db.foodForDay(day)) if (f.meal == FastingLogic.WATER) water += FastingLogic.millilitres(f.amount);

        LinearLayout card = Ui.card(a);
        card.addView(total(a, R.drawable.ic_food, t[0], a.prefs.kcalTarget(), " kcal", R.color.eat, true),
                Ui.lpm(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        card.addView(macroRow(a, "Protein", t[1], a.prefs.proteinTarget(), R.color.protein));
        card.addView(macroRow(a, "Carbs", t[2], a.prefs.carbsTarget(), R.color.carbs));
        card.addView(macroRow(a, "Fat", t[3], a.prefs.fatTarget(), R.color.fat));
        card.addView(total(a, R.drawable.ic_stage_drop, water, a.prefs.waterTarget(), " ml", R.color.water, false),
                Ui.lpm(a, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        return card;
    }

    /** An icon and big total against its target with what's left, over a thick bar; overIsBad turns going over red. */
    private static View total(MainActivity c, int iconRes, double value, int target, String unit, int colorRes, boolean overIsBad) {
        boolean bad = overIsBad && value > target;
        LinearLayout box = Ui.vbox(c);
        LinearLayout big = Ui.hbox(c);
        big.setGravity(Gravity.BOTTOM);
        big.addView(Ui.icon(c, iconRes, colorRes, 24), Ui.lpm(c, Ui.dp(c, 24), Ui.dp(c, 24), 0, 0, 8, 7));
        big.addView(Ui.big(c, Ui.n0(value), 32, R.color.text));
        TextView of = Ui.muted(c, "  / " + Ui.n0(target) + unit);
        of.setPadding(0, 0, 0, Ui.dp(c, 6));
        big.addView(of, Ui.lp(0, Ui.WRAP, 1f));
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
        card.addView(Ui.text(c, "Training", 16, R.color.text, true));
        List<Db.Workout> ws = db.workoutsForDay(Db.today());
        if (ws.isEmpty()) {
            TextView t = Ui.muted(c, "Rest day so far - no workouts logged.");
            t.setPadding(0, Ui.dp(c, 6), 0, 0);
            card.addView(t);
        } else {
            for (Db.Workout w : ws) card.addView(WorkoutsScreen.summaryRow(a, w, false));
        }
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

    private View actions() {
        LinearLayout row = Ui.hbox(c);
        row.setPadding(0, Ui.dp(c, 4), 0, 0);
        if (active != null) {
            Button end = Ui.primary(c, "End fast", R.color.primary);
            end.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { a.push(new EndFastScreen(a, active)); }
            });
            row.addView(end, Ui.lp(0, Ui.dp(c, 52), 1f));
            row.addView(Ui.hspace(c, 12));
            Button log = Ui.outline(c, "Log food", R.color.eat);
            log.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { openFood(); }
            });
            row.addView(log, Ui.lp(0, Ui.dp(c, 52), 1f));
        } else {
            Button start = Ui.outline(c, "Start fast", R.color.primary);
            start.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { a.startFast(); }
            });
            row.addView(start, Ui.lp(0, Ui.dp(c, 52), 1f));
            row.addView(Ui.hspace(c, 12));
            Button log = Ui.primary(c, "Log food", R.color.eat);
            log.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { openFood(); }
            });
            row.addView(log, Ui.lp(0, Ui.dp(c, 52), 1f));
        }
        return row;
    }

    private void openFood() {
        a.selectedDay = LocalDate.now();
        a.showTab(MainActivity.TAB_FOOD);
        Screen s = a.currentScreen();
        if (s instanceof FoodScreen) ((FoodScreen) s).openAdd(-1);
    }
}
