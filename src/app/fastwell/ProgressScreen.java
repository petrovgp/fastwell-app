package app.fastwell;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Weekly / monthly charts for fasting, weight, calories and training, plus the streak. */
final class ProgressScreen extends Screen {
    private static int rangeDays = 7;

    ProgressScreen(MainActivity a) { super(a); }

    @Override
    View build() {
        LinearLayout col = Ui.column(c, 24);
        col.addView(Ui.header(c, "Progress", rangeDays == 7 ? "Last 7 days" : "Last 30 days"));

        LinearLayout toggle = Ui.hbox(c);
        toggle.addView(rangeButton("Week", 7), Ui.lp(0, Ui.dp(c, 44), 1f));
        toggle.addView(Ui.hspace(c, 8));
        toggle.addView(rangeButton("Month", 30), Ui.lp(0, Ui.dp(c, 44), 1f));
        toggle.setPadding(0, 0, 0, Ui.dp(c, 12));
        col.addView(toggle);

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        List<LocalDate> days = FastingLogic.lastDays(today, rangeDays);
        String[] labels = new String[days.size()];
        DateTimeFormatter dow = DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault());
        for (int i = 0; i < days.size(); i++) {
            labels[i] = rangeDays == 7 ? days.get(i).format(dow) : String.valueOf(days.get(i).getDayOfMonth());
        }
        String from = days.get(0).toString(), to = today.toString();
        long since = ZonedDateTime.of(days.get(0).atStartOfDay(), zone).toInstant().toEpochMilli();

        // Streak.
        List<Db.Fast> all = db.completedFasts(1000);
        List<long[]> marks = new ArrayList<long[]>();
        for (Db.Fast f : all) marks.add(new long[] {f.endAt, f.met() ? 1 : 0});
        int[] st = FastingLogic.streaks(marks, today, zone);
        LinearLayout streak = Ui.card(c);
        LinearLayout sr = Ui.hbox(c);
        LinearLayout sl = Ui.vbox(c);
        sl.addView(Ui.big(c, String.valueOf(st[0]), 40, R.color.success));
        sr.addView(sl);
        LinearLayout sm = Ui.vbox(c);
        sm.setPadding(Ui.dp(c, 16), 0, 0, 0);
        sm.addView(Ui.text(c, st[0] == 1 ? "day fasting streak" : "day fasting streak", 16, R.color.text, true));
        sm.addView(Ui.muted(c, "Best " + st[1] + " days  -  " + all.size() + " fasts completed"));
        sr.addView(sm, Ui.lp(0, Ui.WRAP, 1f));
        streak.addView(sr);
        col.addView(streak);

        // Fasting hours per day (attributed to the day the fast ended).
        List<long[]> se = new ArrayList<long[]>();
        for (Db.Fast f : db.completedFastsSince(since)) se.add(new long[] {f.startAt, f.endAt});
        double[] fh = FastingLogic.fastingHoursPerDay(se, days, zone);
        int planH = prefs.plan().fastHours;
        col.addView(chartCard("Fasting hours", avgText(fh, "h") + "  -  goal " + planH + "h", fh, labels, planH,
                R.color.primary, true, null));

        // Weight per logged day.
        Map<String, Double> wt = db.weightByDay(from, to);
        double[] wv = new double[days.size()];
        for (int i = 0; i < days.size(); i++) {
            Double v = wt.get(days.get(i).toString());
            wv[i] = v == null ? 0 : v;
        }
        col.addView(weightCard(wv, labels));

        // Calories per day.
        Map<String, Double> kc = db.kcalByDay(from, to);
        double[] kv = new double[days.size()];
        for (int i = 0; i < days.size(); i++) {
            Double v = kc.get(days.get(i).toString());
            kv[i] = v == null ? 0 : v;
        }
        List<Db.Workout> ws = db.workoutsBetween(from, to);
        double[] raised = new double[days.size()];
        for (Db.Workout w : ws) {
            for (int i = 0; i < days.size(); i++) if (w.day.equals(days.get(i).toString())) raised[i] += w.kcal;
        }
        col.addView(chartCard("Calories", avgText(kv, " kcal") + "  -  target " + Ui.n0(prefs.kcalTarget()), kv, labels,
                prefs.kcalTarget(), R.color.eat, false, raised));

        // Sessions per day, against the target for the training days in range.
        double[] sv = new double[days.size()];
        int mins = 0, fasted = 0;
        for (Db.Workout w : ws) {
            for (int i = 0; i < days.size(); i++) if (w.day.equals(days.get(i).toString())) sv[i]++;
            mins += WorkoutsScreen.minutes(w);
            if (w.fasted) fasted++;
        }
        int daily = prefs.workoutTarget();
        int goal = FastingLogic.sessionTarget(daily, prefs.restDays(), days);
        String wsub = (goal > 0 ? ws.size() + " of " + goal + FastingLogic.sessions(goal) : ws.size() + FastingLogic.sessions(ws.size()))
                + "  -  " + mins + " min" + (ws.isEmpty() ? "" : ", " + fasted + " fasted");
        col.addView(chartCard("Training sessions", wsub, sv, labels, daily, R.color.train, true, null));

        int logged = 0;
        for (int i = 0; i < days.size(); i++) if (fh[i] > 0 || wv[i] > 0 || kv[i] > 0 || sv[i] > 0) logged++;
        if (logged < 3) {
            TextView hint = Ui.muted(c, "Log at least 3 days to see trends.");
            hint.setPadding(Ui.dp(c, 4), 0, 0, 0);
            col.addView(hint);
        }
        return Ui.scroll(c, col);
    }

    private Button rangeButton(String label, final int days) {
        Button b = rangeDays == days ? Ui.tonal(c, label, R.color.primary) : Ui.outline(c, label, R.color.text2);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rangeDays = days;
                a.refresh();
            }
        });
        return b;
    }

    private static String avgText(double[] v, String unit) {
        double sum = 0;
        int n = 0;
        for (double d : v) if (d > 0) { sum += d; n++; }
        if (n == 0) return "No data yet";
        return "Avg " + (unit.equals("h") ? Ui.n1(sum / n) : Ui.n0(sum / n)) + unit + " on " + n + (n == 1 ? " day" : " days");
    }

    private View weightCard(double[] kg, String[] labels) {
        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, "Weight", 16, R.color.text, true));
        card.addView(Ui.muted(c, FastingLogic.weightSummary(kg, rangeDays == 7 ? "this week" : "this month")));
        float[] f = new float[kg.length];
        for (int i = 0; i < kg.length; i++) f[i] = (float) kg[i];
        Views.LineChart chart = new Views.LineChart(c, Ui.col(c, R.color.water));
        chart.setData(f, labels);
        card.addView(chart, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 150), 0, 12, 0, 0));
        return card;
    }

    /** raised, if not null, is how far workouts lifted each day's target; a legend explains it when any day has one. */
    private View chartCard(String title, String sub, double[] values, String[] labels, float target, int colorRes, boolean byTarget,
            double[] raised) {
        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, title, 16, R.color.text, true));
        card.addView(Ui.muted(c, sub));
        float[] f = new float[values.length];
        for (int i = 0; i < values.length; i++) f[i] = (float) values[i];
        Views.Chart chart = new Views.Chart(c, Ui.col(c, colorRes));
        chart.setData(f, labels, target, byTarget);
        card.addView(chart, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 150), 0, 12, 0, 0));
        if (raised != null) {
            float[] extra = new float[raised.length];
            boolean any = false;
            for (int i = 0; i < raised.length; i++) {
                extra[i] = (float) raised[i];
                any |= raised[i] > 0;
            }
            chart.setRaised(extra, Ui.col(c, R.color.train));
            if (any) card.addView(raisedLegend());
        }
        return card;
    }

    private View raisedLegend() {
        LinearLayout row = Ui.hbox(c);
        row.setPadding(0, Ui.dp(c, 8), 0, 0);
        View swatch = new View(c);
        swatch.setBackground(Ui.round(Ui.col(c, R.color.train), Ui.dp(c, 2)));
        row.addView(swatch, Ui.lpm(c, Ui.dp(c, 12), Ui.dp(c, 12), 0, 0, 6, 0));
        row.addView(Ui.muted(c, "Target raised by workouts"));
        return row;
    }
}
