package app.fastwell;

import android.content.res.ColorStateList;
import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen form for logging a finished workout or editing one. Cardio has an activity, distance and note;
 * strength is only its duration for now. The back arrow cancels.
 */
final class LogWorkoutScreen extends Screen {
    private static final int MAX_HOURS = 23;
    private static final int DIAL_DP = 176;

    private final Db.Workout w;
    private final boolean isNew, cardio;

    private int activity, hours, minutes, seconds, rpe;
    private boolean fasted;
    private String otherName = "", distanceText = "", kcalText = "", noteText = "";
    /** Off while editing, so a saved calorie count only changes once something it depends on does. */
    private boolean estimate;

    private final List<Choice> fedChoices = new ArrayList<Choice>();
    private LinearLayout nameBox, estimateCard, typedKcalBox;
    private TextView kcalVal;
    private Button save;

    /** Starts a new workout of the given type on the selected day. */
    LogWorkoutScreen(MainActivity a, int type) {
        super(a);
        w = new Db.Workout();
        w.type = type;
        w.name = type == Db.CARDIO ? "Run" : "Strength workout";
        w.durationSec = (type == Db.CARDIO ? 30 : 60) * 60;
        w.startAt = a.timeOnSelectedDay();
        w.fasted = db.wasFastingAt(w.startAt);
        isNew = true;
        cardio = type == Db.CARDIO;
        load();
    }

    LogWorkoutScreen(MainActivity a, Db.Workout existing) {
        super(a);
        w = existing;
        isNew = false;
        cardio = w.type == Db.CARDIO;
        load();
    }

    private void load() {
        hours = Math.min(MAX_HOURS, w.durationSec / 3600);
        minutes = w.durationSec / 60 % 60;
        seconds = w.durationSec % 60;
        rpe = w.rpe > 0 ? w.rpe : FastingLogic.RPE_DEFAULT;
        fasted = w.fasted;
        estimate = isNew || w.kcal <= 0;
        activity = FastingLogic.OTHER_CARDIO;
        for (int i = 0; i < FastingLogic.CARDIO.length; i++) {
            if (FastingLogic.CARDIO[i].equalsIgnoreCase(w.name)) activity = i;
        }
        // Any name that isn't on the dial, like HIIT from before it left, stays as an Other activity's name.
        if (activity == FastingLogic.OTHER_CARDIO) {
            if (!FastingLogic.CARDIO[activity].equalsIgnoreCase(w.name)) otherName = w.name;
            if (w.kcal > 0) kcalText = Ui.plain(w.kcal);
        }
        if (w.distanceKm > 0) distanceText = Ui.plain(w.distanceKm);
        noteText = w.note;
    }

    /** The saved workout's id, or 0 before the first save. */
    long workoutId() { return w.id; }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        String title = (isNew ? "Log " : "Edit ") + (cardio ? "cardio" : "strength");
        LinearLayout header = FastHistoryScreen.backHeader(a, title);
        header.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0);
        root.addView(header);

        LinearLayout col = Ui.column(c, 24);
        if (cardio) {
            col.addView(Ui.sectionTitle(c, "Activity"));
            col.addView(activityDial(), Ui.lp(Ui.MATCH, Ui.dp(c, DIAL_DP)));
            nameBox = Ui.vbox(c);
            nameBox.addView(textField("Name", "Activity name", Ui.TXT, otherName, new Text() {
                @Override
                public void set(String s) { otherName = s; }
            }));
            col.addView(nameBox);
        }
        col.addView(Ui.sectionTitle(c, "Duration"));
        col.addView(durationDial(), Ui.lp(Ui.MATCH, Ui.dp(c, DIAL_DP)));
        if (cardio) {
            col.addView(textField("Distance in km (optional)", "0", Ui.DEC, distanceText, new Text() {
                @Override
                public void set(String s) { distanceText = s; }
            }));
        }
        col.addView(Ui.sectionTitle(c, "Fasted or fed"));
        col.addView(fedRow());
        col.addView(Ui.sectionTitle(c, "Effort"));
        col.addView(rpeCard());
        col.addView(Ui.sectionTitle(c, "Calories"));
        col.addView(estimateCard());
        typedKcalBox = Ui.vbox(c);
        typedKcalBox.addView(textField("Calories (optional)", "0", Ui.DEC, kcalText, new Text() {
            @Override
            public void set(String s) { kcalText = s; }
        }));
        col.addView(typedKcalBox);
        if (cardio) {
            col.addView(Ui.sectionTitle(c, "Note"));
            col.addView(textField(null, "Note (optional)", Ui.TXT, noteText, new Text() {
                @Override
                public void set(String s) { noteText = s; }
            }), Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        root.addView(Ui.scroll(c, col), Ui.lp(Ui.MATCH, 0, 1f));

        LinearLayout bottom = Ui.hbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        if (!isNew) {
            Button delete = Ui.tonal(c, "Delete", R.color.error);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { delete(); }
            });
            bottom.addView(delete, Ui.lp(0, Ui.dp(c, 56), 1f));
            bottom.addView(Ui.hspace(c, 12));
        }
        save = Ui.primary(c, "Save", R.color.train);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { save(); }
        });
        bottom.addView(save, Ui.lp(0, Ui.dp(c, 56), 1f));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));

        update();
        return root;
    }

    private interface Text { void set(String s); }

    /** A text field that reports what is typed, labelled when label isn't null. */
    private View textField(String label, String hint, int type, String value, final Text to) {
        EditText e = Ui.field(c, hint, type);
        e.setText(value);
        e.addTextChangedListener(new Ui.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                to.set(s.toString());
                update();
            }
        });
        return label == null ? e : Ui.labeled(c, label, e);
    }

    private View activityDial() {
        final Dial d = new Dial(c);
        int col = d.addColumn("ACTIVITY", "", 0, FastingLogic.OTHER_CARDIO, 1, 0, activity);
        d.setFormat(col, new Dial.Format() {
            @Override
            public String of(double v) { return FastingLogic.CARDIO[(int) Math.round(v)]; }
        });
        d.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) {
                activity = (int) Math.round(value);
                estimate = true;
                update();
            }
        });
        return d;
    }

    private View durationDial() {
        Dial d = new Dial(c);
        d.addColumn("HOURS", "h", 0, MAX_HOURS, 1, 0, hours);
        d.addColumn("MINUTES", "min", 0, 59, 1, 0, minutes);
        d.addColumn("SECONDS", "s", 0, 59, 1, 0, seconds);
        d.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) {
                if (column == 0) hours = (int) value;
                else if (column == 1) minutes = (int) value;
                else seconds = (int) value;
                estimate = true;
                update();
            }
        });
        return d;
    }

    private View fedRow() {
        LinearLayout row = Ui.hbox(c);
        final Choice fastedChoice = Choice.toggle(c, "Fasted"), fedChoice = Choice.toggle(c, "Fed");
        fedChoices.clear();
        fedChoices.add(fastedChoice);
        fedChoices.add(fedChoice);
        row.addView(fastedChoice.view, Ui.lp(0, Ui.WRAP, 1f));
        row.addView(Ui.hspace(c, 10));
        row.addView(fedChoice.view, Ui.lp(0, Ui.WRAP, 1f));
        Choice.select(fedChoices, fasted ? fastedChoice : fedChoice);
        fastedChoice.onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) { fasted = true; Choice.select(fedChoices, fastedChoice); }
        });
        fedChoice.onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) { fasted = false; Choice.select(fedChoices, fedChoice); }
        });
        return row;
    }

    private View rpeCard() {
        LinearLayout card = Ui.card(c);
        LinearLayout head = Ui.hbox(c);
        head.addView(Ui.text(c, "RPE", 16, R.color.text, true), Ui.lp(0, Ui.WRAP, 1f));
        final TextView value = Ui.big(c, rpe + " / " + FastingLogic.RPE_MAX, 17, R.color.train);
        head.addView(value);
        card.addView(head);
        SeekBar bar = new SeekBar(c);
        bar.setMin(FastingLogic.RPE_MIN);
        bar.setMax(FastingLogic.RPE_MAX);
        bar.setProgress(rpe);
        ColorStateList train = ColorStateList.valueOf(Ui.col(c, R.color.train));
        bar.setProgressTintList(train);
        bar.setThumbTintList(train);
        bar.setContentDescription("Rate of perceived exertion");
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (!fromUser) return;
                rpe = progress;
                estimate = true;
                value.setText(rpe + " / " + FastingLogic.RPE_MAX);
                update();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        card.addView(bar, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 12, 0, 8));
        LinearLayout ends = Ui.hbox(c);
        ends.addView(Ui.muted(c, "1  Very easy"), Ui.lp(0, Ui.WRAP, 1f));
        ends.addView(Ui.muted(c, "10  Max effort"));
        card.addView(ends);
        return card;
    }

    private View estimateCard() {
        estimateCard = Ui.card(c);
        kcalVal = Ui.big(c, "", 22, R.color.text);
        estimateCard.addView(kcalVal);
        TextView note = Ui.muted(c, "Worked out from the duration, RPE and your weight of " + Ui.n1(prefs.weightKg()) + " kg.");
        note.setPadding(0, Ui.dp(c, 4), 0, 0);
        estimateCard.addView(note);
        return estimateCard;
    }

    private boolean isOther() { return cardio && activity == FastingLogic.OTHER_CARDIO; }

    private int durationSec() { return hours * 3600 + minutes * 60 + seconds; }

    private double kcal() {
        if (isOther()) return Math.max(0, Ui.num(kcalText, 0));
        if (!estimate) return w.kcal;
        return cardio ? FastingLogic.cardioKcal(activity, durationSec(), rpe, prefs.weightKg())
                : FastingLogic.strengthKcal(durationSec(), rpe, prefs.weightKg());
    }

    private void update() {
        if (nameBox != null) nameBox.setVisibility(isOther() ? View.VISIBLE : View.GONE);
        estimateCard.setVisibility(isOther() ? View.GONE : View.VISIBLE);
        typedKcalBox.setVisibility(isOther() ? View.VISIBLE : View.GONE);
        kcalVal.setText(Ui.n0(kcal()) + " kcal");
        boolean ok = durationSec() > 0;
        save.setEnabled(ok);
        save.setAlpha(ok ? 1f : 0.4f);
    }

    private void save() {
        if (durationSec() <= 0) return;
        w.durationSec = durationSec();
        w.kcal = kcal();
        if (estimate) w.rpe = rpe;
        w.fasted = fasted;
        if (cardio) {
            String typed = otherName.trim();
            w.name = isOther() && !typed.isEmpty() ? typed : FastingLogic.CARDIO[activity];
            w.distanceKm = Math.max(0, Ui.num(distanceText, 0));
            w.note = noteText.trim();
        }
        if (isNew) {
            // Ending now, but never starting before the day it's logged on.
            long dayStart = a.selectedDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            w.startAt = Math.max(dayStart, w.startAt - w.durationSec * 1000L);
        }
        w.endAt = w.startAt + w.durationSec * 1000L;
        if (isNew) db.addWorkout(w); else db.updateWorkout(w);
        hideKeyboard();
        a.pop();
        a.snack(w.name + " saved  -  " + FastingLogic.workoutDur(w.durationSec), null);
    }

    private void delete() {
        final List<Db.WSet> sets = db.sets(w.id);
        db.deleteWorkout(w.id);
        hideKeyboard();
        a.pop();
        a.snack("Workout deleted", new Runnable() {
            @Override
            public void run() {
                db.addWorkout(w);
                for (Db.WSet s : sets) {
                    s.workoutId = w.id;
                    db.addSet(s);
                }
            }
        });
    }
}
