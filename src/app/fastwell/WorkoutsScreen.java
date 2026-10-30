package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/** Workout journal for the selected day. */
final class WorkoutsScreen extends Screen {

    WorkoutsScreen(MainActivity a) { super(a); }

    @Override
    DayPickerScreen dayPicker() {
        return new DayPickerScreen(a, "Choose workout day", R.color.train, new DayPickerScreen.Summary() {
            @Override
            public String of(LocalDate d) {
                List<Db.Workout> ws = db.workoutsForDay(d.toString());
                if (ws.isEmpty()) return "No workouts logged";
                int mins = 0;
                for (Db.Workout w : ws) mins += minutes(w);
                return ws.size() + (ws.size() == 1 ? " session" : " sessions") + "  -  " + mins + " min";
            }
        });
    }

    @Override
    View build() {
        FrameLayout root = new FrameLayout(c);
        LinearLayout col = Ui.column(c, 96);
        col.addView(Ui.header(c, "Workouts", "Strength and cardio"));
        col.addView(a.dateStrip(true));
        // Everything for the day itself, so it can slide in when the day changes.
        LinearLayout body = Ui.vbox(c);
        col.addView(body);
        Ui.slideIn(body, a.daySlide, 48);

        List<Db.Workout> ws = db.workoutsForDay(a.selectedDay.toString());
        LinearLayout target = Ui.card(c);
        target.addView(TodayScreen.workoutTotal(a, a.selectedDay, ws.size()));
        body.addView(target);
        if (ws.isEmpty()) {
            LinearLayout empty = Ui.card(c);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(Ui.icon(c, R.drawable.ic_workout, R.color.train, 40));
            TextView t = Ui.body(c, "No workouts logged for this day.");
            t.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
            empty.addView(t);
            empty.addView(Ui.muted(c, "Tap Add workout to log a cardio or strength session."));
            body.addView(empty);
        } else {
            int mins = 0;
            double vol = 0;
            for (Db.Workout w : ws) {
                mins += minutes(w);
                if (w.type == Db.STRENGTH) vol += db.workoutStats(w.id)[2];
            }
            TextView sum = Ui.muted(c, ws.size() + FastingLogic.sessions(ws.size()) + "  -  " + mins + " min"
                    + (vol > 0 ? "  -  " + Ui.n0(vol) + " kg lifted" : ""));
            sum.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 8));
            body.addView(sum);
            for (final Db.Workout w : ws) {
                LinearLayout card = Ui.card(c);
                card.addView(summaryRow(a, w, true));
                Ui.clickable(card, 16);
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { open(a, w); }
                });
                body.addView(card);
            }
        }
        root.addView(Ui.scroll(c, col), new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        Button fab = Ui.fab(c, "Add workout", R.color.train);
        root.addView(fab);
        new SpeedDial(root, fab, R.color.train, new String[] {"Cardio", "Strength"}, new SpeedDial.Pick() {
            @Override
            public void on(int which) {
                a.push(new LogWorkoutScreen(a, which == 0 ? Db.CARDIO : Db.STRENGTH));
            }
        });
        return root;
    }

    static int minutes(Db.Workout w) { return Math.round(w.durationSec / 60f); }

    static void open(MainActivity a, Db.Workout w) {
        a.push(new LogWorkoutScreen(a, w));
    }

    /** One-line summary used on the Workouts and Today screens. */
    static View summaryRow(final MainActivity a, final Db.Workout w, boolean big) {
        LinearLayout r = Ui.hbox(a);
        r.setPadding(0, Ui.dp(a, big ? 0 : 8), 0, 0);
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Ui.round(Ui.alpha(Ui.col(a, R.color.train), Ui.night(a) ? 0.22f : 0.14f), Ui.dp(a, 12)));
        ImageView ic = Ui.icon(a, R.drawable.ic_workout, R.color.train, 22);
        badge.addView(ic, new FrameLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22), Gravity.CENTER));
        r.addView(badge, Ui.lp(Ui.dp(a, 44), Ui.dp(a, 44)));
        r.addView(Ui.hspace(a, 12));
        LinearLayout mid = Ui.vbox(a);
        String name = w.name.isEmpty() ? (w.type == Db.STRENGTH ? "Strength workout" : "Cardio") : w.name;
        mid.addView(Ui.text(a, name, 16, R.color.text, true));
        String detail = FastingLogic.workoutDur(w.durationSec) + (w.distanceKm > 0 ? ", " + Ui.n1(w.distanceKm) + " km" : "")
                + (w.kcal > 0 ? ", " + Ui.n0(w.kcal) + " kcal" : "");
        // Only workouts from before sets stopped being logged have a volume.
        if (w.type == Db.STRENGTH) {
            double volume = a.db.workoutStats(w.id)[2];
            if (volume > 0) detail += ", " + Ui.n0(volume) + " kg";
        }
        mid.addView(Ui.muted(a, Ui.time(a, w.startAt) + "  -  " + detail));
        r.addView(mid, Ui.lp(0, Ui.WRAP, 1f));
        r.addView(Ui.chip(a, w.fasted ? "Fasted" : "Fed", w.fasted ? R.color.primary : R.color.eat));
        return r;
    }
}
