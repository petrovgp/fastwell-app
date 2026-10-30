package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
            empty.addView(Ui.muted(c, "Tap Start workout to log strength sets or a cardio session."));
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
        Button fab = Ui.fab(c, "Start workout", R.color.train);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startDialog(); }
        });
        root.addView(fab);
        return root;
    }

    static int minutes(Db.Workout w) {
        if (w.type == Db.CARDIO) return w.durationMin;
        if (w.endAt > 0) return (int) ((w.endAt - w.startAt) / 60000);
        return (int) ((System.currentTimeMillis() - w.startAt) / 60000);
    }

    static void open(MainActivity a, Db.Workout w) {
        if (w.type == Db.STRENGTH) a.push(new WorkoutEditScreen(a, w.id));
        else cardioDialog(a, w);
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
        String detail;
        if (w.type == Db.STRENGTH) {
            double[] s = a.db.workoutStats(w.id);
            detail = (int) s[3] + (s[3] == 1 ? " exercise" : " exercises") + ", " + (int) s[0] + " sets"
                    + (s[2] > 0 ? ", " + Ui.n0(s[2]) + " kg" : "");
            detail += "  -  " + (w.endAt == 0 ? "in progress" : minutes(w) + " min");
        } else {
            detail = w.durationMin + " min" + (w.distanceKm > 0 ? ", " + Ui.n1(w.distanceKm) + " km" : "")
                    + (w.kcal > 0 ? ", " + Ui.n0(w.kcal) + " kcal" : "");
        }
        mid.addView(Ui.muted(a, Ui.time(a, w.startAt) + "  -  " + detail));
        r.addView(mid, Ui.lp(0, Ui.WRAP, 1f));
        r.addView(Ui.chip(a, w.fasted ? "Fasted" : "Fed", w.fasted ? R.color.primary : R.color.eat));
        return r;
    }

    private void startDialog() {
        new AlertDialog.Builder(c)
                .setTitle("Start workout")
                .setItems(new String[] {"Strength training  (sets, reps, weight)", "Cardio  (time, distance)"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (which == 0) startStrength(); else cardioDialog(a, null);
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startStrength() {
        Db.Workout w = new Db.Workout();
        w.type = Db.STRENGTH;
        w.name = "Strength workout";
        w.startAt = a.timeOnSelectedDay();
        boolean today = a.selectedDay.equals(java.time.LocalDate.now());
        w.endAt = today ? 0 : w.startAt + 60 * 60000L;
        w.fasted = db.wasFastingAt(w.startAt);
        db.addWorkout(w);
        a.push(new WorkoutEditScreen(a, w.id));
    }

    static final String[] CARDIO = {"Run", "Walk", "Cycle", "Swim", "Row", "HIIT", "Elliptical", "Hike"};

    static void cardioDialog(final MainActivity a, final Db.Workout existing) {
        final Db.Workout w;
        if (existing != null) {
            w = existing;
        } else {
            w = new Db.Workout();
            w.type = Db.CARDIO;
            w.name = "Run";
            w.durationMin = 30;
            w.startAt = a.timeOnSelectedDay() - 30 * 60000L;
            w.fasted = a.db.wasFastingAt(w.startAt);
        }
        LinearLayout body = Ui.dialogBody(a);
        final EditText name = Ui.field(a, "Activity", Ui.TXT);
        name.setText(w.name);
        body.addView(Ui.labeled(a, "Activity", name));
        LinearLayout chips = Ui.hbox(a);
        for (final String n : CARDIO) {
            TextView chip = Ui.chip(a, n, R.color.train);
            LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
            lp.setMarginEnd(Ui.dp(a, 6));
            Ui.clickable(chip, 12);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { name.setText(n); }
            });
            chips.addView(chip, lp);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        hs.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
        body.addView(hs);

        final Button start = Ui.tonal(a, "", R.color.train);
        final long[] startAt = {w.startAt};
        start.setText("Started " + Ui.dateTime(a, startAt[0]) + "  (change)");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.pickDateTime(startAt[0], new MainActivity.LongCb() {
                    @Override
                    public void on(long t) {
                        startAt[0] = t;
                        start.setText("Started " + Ui.dateTime(a, t) + "  (change)");
                    }
                });
            }
        });
        body.addView(start, Ui.lpm(a, Ui.MATCH, Ui.dp(a, 44), 0, 8, 0, 4));

        final EditText dur = Ui.field(a, "Minutes", Ui.NUM);
        dur.setText(String.valueOf(w.durationMin));
        final EditText dist = Ui.field(a, "km (optional)", Ui.DEC);
        if (w.distanceKm > 0) dist.setText(Ui.plain(w.distanceKm));
        final EditText kcal = Ui.field(a, "kcal (optional)", Ui.NUM);
        if (w.kcal > 0) kcal.setText(Ui.plain(w.kcal));
        LinearLayout row = Ui.hbox(a);
        row.addView(Ui.labeled(a, "Duration min", dur), Ui.lp(0, Ui.WRAP, 1f));
        row.addView(Ui.labeled(a, "Distance km", dist), Ui.lp(0, Ui.WRAP, 1f));
        row.addView(Ui.labeled(a, "Calories", kcal), Ui.lp(0, Ui.WRAP, 1f));
        body.addView(row);
        final CheckBox fasted = new CheckBox(a);
        fasted.setText("Done fasted");
        fasted.setChecked(w.fasted);
        body.addView(fasted);
        final EditText note = Ui.field(a, "Note (optional)", Ui.TXT);
        note.setText(w.note);
        body.addView(note, Ui.lp(Ui.MATCH, Ui.WRAP));
        ScrollView sv = new ScrollView(a);
        sv.addView(body);

        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(existing == null ? "Log cardio" : "Edit cardio")
                .setView(sv)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        int minutes = (int) Ui.num(dur, 0);
                        if (minutes <= 0) { Ui.toast(a, "Enter a duration"); return; }
                        String n = name.getText().toString().trim();
                        boolean startChanged = startAt[0] != w.startAt;
                        w.name = n.isEmpty() ? "Cardio" : n;
                        w.startAt = startAt[0];
                        w.durationMin = minutes;
                        w.endAt = w.startAt + minutes * 60000L;
                        w.distanceKm = Math.max(0, Ui.num(dist, 0));
                        w.kcal = Math.max(0, Ui.num(kcal, 0));
                        w.note = note.getText().toString().trim();
                        w.fasted = startChanged && fasted.isChecked() == w.fasted ? a.db.wasFastingAt(w.startAt) : fasted.isChecked();
                        if (existing == null) a.db.addWorkout(w); else a.db.updateWorkout(w);
                        a.refresh();
                        a.snack(w.name + " saved  -  " + minutes + " min", null);
                    }
                })
                .setNegativeButton("Cancel", null);
        if (existing != null) {
            b.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    a.db.deleteWorkout(w.id);
                    a.refresh();
                    a.snack("Workout deleted", new Runnable() {
                        @Override
                        public void run() { a.db.addWorkout(w); }
                    });
                }
            });
        }
        b.show();
    }
}
