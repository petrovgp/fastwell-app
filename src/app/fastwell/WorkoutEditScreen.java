package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Active (or past) strength workout: exercises, set rows with last-session hints, finish. */
final class WorkoutEditScreen extends Screen {
    final long workoutId;
    private Db.Workout w;
    private TextView clock;

    WorkoutEditScreen(MainActivity a, long workoutId) {
        super(a);
        this.workoutId = workoutId;
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        w = db.getWorkout(workoutId);
        LinearLayout col = Ui.column(c, 32);
        if (w == null) {
            col.addView(FastHistoryScreen.backHeader(a, "Workout"));
            col.addView(Ui.muted(c, "This workout was deleted."));
            return Ui.scroll(c, col);
        }

        LinearLayout head = Ui.hbox(c);
        head.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        ImageView back = Ui.iconButton(c, R.drawable.ic_left, R.color.text, "Back");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.handleBack(); }
        });
        head.addView(back);
        final EditText name = Ui.field(c, "Workout name", Ui.TXT);
        name.setText(w.name);
        name.setTextSize(20);
        name.setBackground(null);
        name.setSelectAllOnFocus(false);
        name.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                w.name = s.toString().trim();
                db.updateWorkout(w);
            }
        });
        head.addView(name, Ui.lp(0, Ui.WRAP, 1f));
        Button finish = Ui.primary(c, w.endAt == 0 ? "Finish" : "Done", R.color.train);
        finish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
        head.addView(finish, Ui.lp(Ui.WRAP, Ui.dp(c, 44)));
        col.addView(head);

        LinearLayout info = Ui.hbox(c);
        info.setPadding(Ui.dp(c, 8), 0, 0, Ui.dp(c, 12));
        clock = Ui.big(c, "", 15, R.color.text);
        info.addView(clock);
        TextView started = Ui.text(c, "  -  started " + Ui.dateTime(c, w.startAt), 13, R.color.primary, false);
        Ui.clickable(started, 8);
        started.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.pickDateTime(w.startAt, new MainActivity.LongCb() {
                    @Override
                    public void on(long t) {
                        long len = w.endAt > 0 ? w.endAt - w.startAt : 0;
                        w.startAt = t;
                        if (w.endAt > 0) w.endAt = t + len;
                        w.fasted = db.wasFastingAt(t);
                        db.updateWorkout(w);
                        a.refresh();
                    }
                });
            }
        });
        info.addView(started, Ui.lp(0, Ui.WRAP, 1f));
        TextView chip = Ui.chip(c, w.fasted ? "Fasted" : "Fed", w.fasted ? R.color.primary : R.color.eat);
        Ui.clickable(chip, 12);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                w.fasted = !w.fasted;
                db.updateWorkout(w);
                a.refresh();
            }
        });
        info.addView(chip);
        col.addView(info);

        // Group sets by exercise, in the order exercises were added.
        List<Db.WSet> sets = db.sets(workoutId);
        Map<Long, List<Db.WSet>> byEx = new LinkedHashMap<Long, List<Db.WSet>>();
        for (Db.WSet s : sets) {
            List<Db.WSet> l = byEx.get(s.exerciseId);
            if (l == null) {
                l = new ArrayList<Db.WSet>();
                byEx.put(s.exerciseId, l);
            }
            l.add(s);
        }
        if (byEx.isEmpty()) {
            LinearLayout empty = Ui.card(c);
            empty.addView(Ui.body(c, "Add your first exercise."));
            empty.addView(Ui.muted(c, "Sets are prefilled with what you did last time."));
            col.addView(empty);
        }
        for (Map.Entry<Long, List<Db.WSet>> e : byEx.entrySet()) col.addView(exerciseCard(e.getKey(), e.getValue()));

        Button addEx = Ui.tonal(c, "+  Add exercise", R.color.train);
        addEx.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { pickExercise(); }
        });
        col.addView(addEx, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 52), 0, 4, 0, 16));

        Button del = Ui.flat(c, "Delete workout", R.color.error);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(c)
                        .setTitle("Delete this workout?")
                        .setMessage("All of its sets will be removed.")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                db.deleteWorkout(workoutId);
                                a.pop();
                                a.snack("Workout deleted", null);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        col.addView(del, Ui.lp(Ui.MATCH, Ui.dp(c, 48)));
        return Ui.scroll(c, col);
    }

    @Override
    void tick() {
        if (clock == null || w == null) return;
        long end = w.endAt > 0 ? w.endAt : System.currentTimeMillis();
        clock.setText((w.endAt > 0 ? "Duration " : "Elapsed ") + FastingLogic.hms(end - w.startAt));
    }

    private View exerciseCard(final long exerciseId, final List<Db.WSet> sets) {
        LinearLayout card = Ui.card(c);
        LinearLayout head = Ui.hbox(c);
        head.addView(Ui.text(c, sets.get(0).exerciseName, 17, R.color.text, true), Ui.lp(0, Ui.WRAP, 1f));
        ImageView rm = Ui.iconButton(c, R.drawable.ic_close, R.color.text2, "Remove exercise");
        rm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final List<Db.WSet> removed = db.sets(workoutId);
                db.deleteExerciseFromWorkout(workoutId, exerciseId);
                a.refresh();
                a.snack("Removed " + sets.get(0).exerciseName, new Runnable() {
                    @Override
                    public void run() {
                        for (Db.WSet s : removed) if (s.exerciseId == exerciseId) db.addSet(s);
                    }
                });
            }
        });
        head.addView(rm);
        card.addView(head);

        final List<Db.WSet> last = db.lastSessionSets(exerciseId, workoutId);
        if (!last.isEmpty()) {
            StringBuilder sb = new StringBuilder("Last time: ");
            for (int i = 0; i < last.size(); i++) {
                if (i > 0) sb.append(",  ");
                sb.append(last.get(i).reps).append(" x ").append(Ui.n1(last.get(i).weight)).append(" kg");
            }
            card.addView(Ui.muted(c, sb.toString()));
        }

        LinearLayout labels = Ui.hbox(c);
        labels.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2));
        labels.addView(Ui.muted(c, "Set"), Ui.lp(Ui.dp(c, 36), Ui.WRAP));
        labels.addView(Ui.muted(c, "kg"), Ui.lp(0, Ui.WRAP, 1f));
        labels.addView(Ui.muted(c, "Reps"), Ui.lp(0, Ui.WRAP, 1f));
        labels.addView(Ui.muted(c, "Done"), Ui.lp(Ui.dp(c, 92), Ui.WRAP));
        card.addView(labels);

        for (int i = 0; i < sets.size(); i++) card.addView(setRow(i, sets.get(i), i < last.size() ? last.get(i) : null));

        Button add = Ui.flat(c, "+  Add set", R.color.train);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Db.WSet prev = sets.get(sets.size() - 1);
                Db.WSet hint = sets.size() < last.size() ? last.get(sets.size()) : null;
                Db.WSet s = new Db.WSet();
                s.workoutId = workoutId;
                s.exerciseId = exerciseId;
                s.ord = db.nextOrd(workoutId);
                s.reps = hint != null ? hint.reps : prev.reps;
                s.weight = hint != null ? hint.weight : prev.weight;
                db.addSet(s);
                a.refresh();
            }
        });
        LinearLayout addRow = Ui.hbox(c);
        addRow.addView(add, Ui.lp(Ui.WRAP, Ui.dp(c, 40)));
        card.addView(addRow);
        return card;
    }

    private View setRow(int index, final Db.WSet s, Db.WSet prevSession) {
        LinearLayout r = Ui.hbox(c);
        TextView n = Ui.big(c, String.valueOf(index + 1), 15, R.color.text2);
        r.addView(n, Ui.lp(Ui.dp(c, 36), Ui.WRAP));
        final EditText kg = Ui.field(c, prevSession != null ? Ui.n1(prevSession.weight) : "0", Ui.DEC);
        if (s.weight > 0) kg.setText(Ui.plain(s.weight));
        kg.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable e) {
                s.weight = Math.max(0, Ui.num(kg, 0));
                db.updateSet(s);
            }
        });
        r.addView(kg, Ui.lpm(c, 0, Ui.WRAP, 0, 0, 8, 0));
        ((LinearLayout.LayoutParams) kg.getLayoutParams()).weight = 1f;
        final EditText reps = Ui.field(c, prevSession != null ? String.valueOf(prevSession.reps) : "0", Ui.NUM);
        if (s.reps > 0) reps.setText(String.valueOf(s.reps));
        reps.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable e) {
                s.reps = (int) Math.max(0, Ui.num(reps, 0));
                db.updateSet(s);
            }
        });
        r.addView(reps, Ui.lp(0, Ui.WRAP, 1f));
        final CheckBox done = new CheckBox(c);
        done.setChecked(s.done);
        done.setContentDescription("Set done");
        done.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                s.done = on;
                db.updateSet(s);
            }
        });
        r.addView(done, Ui.lp(Ui.dp(c, 48), Ui.WRAP));
        ImageView del = Ui.iconButton(c, R.drawable.ic_close, R.color.text2, "Delete set");
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                db.deleteSet(s.id);
                a.refresh();
            }
        });
        r.addView(del);
        return r;
    }

    private void pickExercise() {
        LinearLayout body = Ui.dialogBody(c);
        final EditText q = Ui.field(c, "Search or type a new exercise", Ui.TXT);
        body.addView(q, Ui.lp(Ui.MATCH, Ui.WRAP));
        final LinearLayout list = Ui.vbox(c);
        ScrollView sv = new ScrollView(c);
        sv.addView(list);
        body.addView(sv, Ui.lp(Ui.MATCH, Ui.dp(c, 360)));
        final AlertDialog dlg = new AlertDialog.Builder(c)
                .setTitle("Add exercise")
                .setView(body)
                .setNegativeButton("Cancel", null)
                .create();
        final Runnable fill = new Runnable() {
            @Override
            public void run() {
                list.removeAllViews();
                final String query = q.getText().toString().trim();
                List<Db.Exercise> ex = db.exercises(query);
                boolean exact = false;
                for (Db.Exercise e : ex) if (e.name.equalsIgnoreCase(query)) exact = true;
                if (!query.isEmpty() && !exact) {
                    TextView create = Ui.text(c, "+  Create \"" + query + "\"", 15, R.color.train, true);
                    create.setPadding(Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12));
                    Ui.clickable(create, 8);
                    create.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            long id = db.addExercise(query, "Custom");
                            dlg.dismiss();
                            addExercise(id);
                        }
                    });
                    list.addView(create);
                }
                for (final Db.Exercise e : ex) {
                    LinearLayout r = Ui.hbox(c);
                    r.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
                    r.addView(Ui.text(c, e.name, 15, R.color.text, false), Ui.lp(0, Ui.WRAP, 1f));
                    r.addView(Ui.muted(c, e.muscle));
                    Ui.clickable(r, 8);
                    r.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dlg.dismiss();
                            addExercise(e.id);
                        }
                    });
                    list.addView(r);
                }
            }
        };
        q.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) { fill.run(); }
        });
        fill.run();
        dlg.show();
    }

    /** Adds an exercise prefilled with last session's sets (or one empty set). */
    private void addExercise(long exerciseId) {
        List<Db.WSet> last = db.lastSessionSets(exerciseId, workoutId);
        int ord = db.nextOrd(workoutId);
        if (last.isEmpty()) {
            Db.WSet s = new Db.WSet();
            s.workoutId = workoutId;
            s.exerciseId = exerciseId;
            s.ord = ord;
            db.addSet(s);
        } else {
            for (Db.WSet p : last) {
                Db.WSet s = new Db.WSet();
                s.workoutId = workoutId;
                s.exerciseId = exerciseId;
                s.ord = ord++;
                s.reps = p.reps;
                s.weight = p.weight;
                db.addSet(s);
            }
        }
        a.refresh();
        a.snack(last.isEmpty() ? "Exercise added" : "Prefilled with last session's " + last.size() + " sets", null);
    }

    private void finish() {
        List<Db.WSet> sets = db.sets(workoutId);
        // Finishing means the logged sets were done; tick any filled-in sets the user didn't check.
        for (Db.WSet s : sets) {
            if (!s.done && s.reps > 0) {
                s.done = true;
                db.updateSet(s);
            }
        }
        if (w.endAt == 0) w.endAt = System.currentTimeMillis();
        w.durationMin = (int) ((w.endAt - w.startAt) / 60000);
        db.updateWorkout(w);
        double[] st = db.workoutStats(workoutId);
        a.pop();
        a.snack("Workout saved  -  " + FastingLogic.dur(w.endAt - w.startAt)
                + (st[2] > 0 ? "  -  " + Ui.n0(st[2]) + " kg" : ""), null);
    }

    abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
    }
}
