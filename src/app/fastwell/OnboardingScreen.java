package app.fastwell;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * First run, six steps: disclaimer, fasting plan, about you, daily target, water target, workout target. Continue is pinned at the bottom.
 * Settings opens about you, daily target, water target or workout target on its own, with the same layout.
 */
final class OnboardingScreen extends Screen {
    static final int DISCLAIMER = 0, PLAN = 1, ABOUT = 2, TARGETS = 3, WATER = 4, WORKOUTS = 5;
    /** Largest calorie surplus or deficit, in percent. */
    static final int ADJUST_MAX = 30;

    private final int[] steps;
    private final boolean settings;
    int step;
    private int direction;
    private boolean accepted;
    private String planId = "16:8";
    private int customHours = 16;
    private SettingsScreen.ProfileForm form;

    private int adjust;
    private int[] split;
    /** Macro columns, least recently changed first; dragging one takes the difference from the others in this order. */
    private final int[] order = {FastingLogic.CARBS, FastingLogic.FAT, FastingLogic.PROTEIN};
    private Dial macroDial;
    private TextView targetKcal, targetNote;
    private final TextView[] grams = new TextView[3];
    private int waterMl;
    private int workouts;
    private int restDays;

    OnboardingScreen(MainActivity a) { this(a, new int[] {DISCLAIMER, PLAN, ABOUT, TARGETS, WATER, WORKOUTS}, false, 0); }

    private OnboardingScreen(MainActivity a, int[] steps, boolean settings, int step) {
        super(a);
        this.steps = steps;
        this.settings = settings;
        this.step = step;
        waterMl = prefs.waterTarget();
        workouts = prefs.workoutTarget();
        restDays = prefs.restDays();
    }

    /** A single step (ABOUT, TARGETS, WATER or WORKOUTS) opened from Settings. */
    static OnboardingScreen fromSettings(MainActivity a, int kind) {
        return new OnboardingScreen(a, new int[] {kind}, true, 0);
    }

    boolean canGoBack() { return settings || step > 0; }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));
        root.addView(topBar());

        int kind = steps[step];
        LinearLayout col;
        if (kind == TARGETS) {
            // Dials share the remaining height, so this step fits one screen without scrolling.
            col = Ui.column(c, 4);
            targets(col);
            root.addView(col, Ui.lp(Ui.MATCH, 0, 1f));
        } else {
            col = Ui.column(c, 24);
            if (kind == DISCLAIMER) disclaimer(col);
            else if (kind == PLAN) plans(col);
            else if (kind == WATER) water(col);
            else if (kind == WORKOUTS) workouts(col);
            else about(col);
            root.addView(Ui.scroll(c, col), Ui.lp(Ui.MATCH, 0, 1f));
        }

        LinearLayout bottom = Ui.vbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        boolean last = step == steps.length - 1;
        String label = !last ? "Continue" : !settings ? "Get started" : kind == ABOUT ? "Save profile"
                : kind == WATER || kind == WORKOUTS ? "Save target" : "Save targets";
        final Button next = Ui.primary(c, label, R.color.primary);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { next(); }
        });
        if (kind == DISCLAIMER) {
            setEnabled(next, accepted);
            gate = next;
        }
        bottom.addView(next, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));

        if (direction != 0) {
            col.setTranslationX(direction * Ui.dp(c, 56));
            col.setAlpha(0f);
            col.animate().translationX(0).alpha(1f).setDuration(280)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
            direction = 0;
        }
        return root;
    }

    private Button gate;

    private void setEnabled(Button b, boolean on) {
        b.setEnabled(on);
        b.setAlpha(on ? 1f : 0.4f);
    }

    private View topBar() {
        LinearLayout bar = Ui.hbox(c);
        bar.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 4));
        if (canGoBack()) {
            ImageView back = Ui.iconButton(c, R.drawable.ic_left, R.color.text, "Back");
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { back(); }
            });
            bar.addView(back);
        } else {
            bar.addView(Ui.hspace(c, 44));
        }
        if (settings) return bar; // a single step: no progress
        LinearLayout segs = Ui.hbox(c);
        for (int i = 0; i < steps.length; i++) {
            View s = new View(c);
            s.setBackground(Ui.round(i <= step ? Ui.col(c, R.color.primary) : Ui.col(c, R.color.outline), Ui.dp(c, 3)));
            LinearLayout.LayoutParams lp = Ui.lp(0, Ui.dp(c, 6), 1f);
            if (i > 0) lp.setMarginStart(Ui.dp(c, 6));
            segs.addView(s, lp);
        }
        bar.addView(segs, Ui.lpm(c, 0, Ui.WRAP, 8, 0, 12, 0));
        ((LinearLayout.LayoutParams) segs.getLayoutParams()).weight = 1f;
        bar.addView(Ui.text(c, (step + 1) + " of " + steps.length, 13, R.color.text2, true));
        return bar;
    }

    static TextView title(Context c, String s) {
        TextView t = Ui.text(c, s, 28, R.color.text, true);
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 12), 0, Ui.dp(c, 4));
        return t;
    }

    static TextView subtitle(Context c, String s) {
        TextView t = Ui.text(c, s, 15, R.color.text2, false);
        t.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 16));
        return t;
    }

    // ---------- disclaimer ----------

    private void disclaimer(LinearLayout col) {
        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.logo_mark);
        logo.setContentDescription("Fastwell logo");
        LinearLayout.LayoutParams lp = Ui.lpm(c, Ui.dp(c, 64), Ui.dp(c, 64), 4, 16, 0, 4);
        col.addView(logo, lp);
        col.addView(title(c, "Welcome to Fastwell"));
        col.addView(subtitle(c, "Track your fasts, meals and workouts in one place."));

        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, "Before you start", 17, R.color.text, true));
        TextView intro = Ui.body(c, "Fastwell gives general information, not medical advice. Talk to a doctor before fasting if you:");
        intro.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        card.addView(intro);
        String[] who = {
            "are pregnant or breastfeeding",
            "have diabetes or take medication",
            "are under 18",
            "have a history of eating disorders",
        };
        for (String w : who) {
            LinearLayout r = Ui.hbox(c);
            r.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), 0, Ui.dp(c, 4));
            View dot = new View(c);
            dot.setBackground(Ui.round(Ui.col(c, R.color.eat), Ui.dp(c, 3)));
            r.addView(dot, Ui.lp(Ui.dp(c, 6), Ui.dp(c, 6)));
            TextView t = Ui.body(c, w);
            t.setPadding(Ui.dp(c, 12), 0, 0, 0);
            r.addView(t);
            card.addView(r);
        }
        col.addView(card);

        LinearLayout ack = Ui.card(c);
        final CheckBox ok = new CheckBox(c);
        ok.setText("I've read this and want to continue");
        ok.setTextSize(15);
        ok.setTextColor(Ui.col(c, R.color.text));
        ok.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.col(c, R.color.primary)));
        ok.setChecked(accepted);
        ok.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                accepted = on;
                if (gate != null) setEnabled(gate, on);
            }
        });
        ack.addView(ok);
        col.addView(ack);
    }

    // ---------- fasting plan ----------

    private void plans(LinearLayout col) {
        col.addView(title(c, "Your fasting plan"));
        col.addView(subtitle(c, "How long do you want to fast each day? You can change this any time."));
        PlanCards.add(a, col, planId, customHours, new PlanCards.Cb() {
            @Override
            public void on(String id, int h) {
                planId = id;
                customHours = h;
            }
        });
        TextView hint = Ui.muted(c, "New to fasting? 13:11 or 16:8 are good places to start.");
        hint.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), 0, 0);
        col.addView(hint);
    }

    // ---------- about you ----------

    private void about(LinearLayout col) {
        col.addView(title(c, "About you"));
        col.addView(subtitle(c, "Used only to suggest a daily calorie target. Stored on this phone."));
        SettingsScreen.ProfileForm f = form();
        ViewGroup parent = (ViewGroup) f.view.getParent();
        if (parent != null) parent.removeView(f.view);
        LinearLayout card = Ui.card(c);
        card.addView(f.view);
        col.addView(card);
    }

    private SettingsScreen.ProfileForm form() {
        if (form == null) form = new SettingsScreen.ProfileForm(a, prefs);
        return form;
    }

    // ---------- daily target ----------

    /** Faded maintenance on top, a surplus/deficit dial with the resulting target, then a carbs/protein/fat split dial. */
    private void targets(LinearLayout col) {
        initTargets();
        final int maintenance = form().kcal();
        col.addView(title(c, "Daily target"));
        col.addView(subtitle(c, "Adjust calories, then split into macros."));

        // Read-only, drawn faded like a dial's derived column.
        int faded = Ui.alpha(Ui.col(c, R.color.text2), 0.5f);
        LinearLayout base = Ui.vbox(c);
        base.setGravity(Gravity.CENTER_HORIZONTAL);
        base.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
        base.setBackground(Ui.round(Ui.alpha(Ui.col(c, R.color.text), 0.04f), Ui.dp(c, 20)));
        base.addView(dialLabel("MAINTENANCE", true));
        base.addView(amount(Ui.big(c, Ui.n0(maintenance), 28, R.color.text2), faded));
        col.addView(base, Ui.lp(Ui.MATCH, Ui.WRAP));

        LinearLayout row = Ui.hbox(c);
        Dial adjustDial = new Dial(c);
        adjustDial.addColumn("ADJUST", "%", -ADJUST_MAX, ADJUST_MAX, 5, 0, adjust);
        adjustDial.setFormat(0, new Dial.Format() {
            @Override
            public String of(double v) { return (v > 0 ? "+" : "") + Math.round(v); }
        });
        adjustDial.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) {
                adjust = (int) Math.round(value);
                updateTargets(maintenance);
            }
        });
        row.addView(adjustDial, Ui.lp(0, Ui.MATCH, 1f));
        row.addView(Ui.hspace(c, 10));
        LinearLayout result = Ui.vbox(c);
        result.setGravity(Gravity.CENTER);
        result.setBackground(Ui.round(Ui.alpha(Ui.col(c, R.color.primary), Ui.night(c) ? 0.2f : 0.08f), Ui.dp(c, 20)));
        result.addView(dialLabel("DAILY TARGET", false));
        targetKcal = Ui.big(c, "", 28, R.color.text);
        result.addView(amount(targetKcal, Ui.col(c, R.color.text)));
        targetNote = Ui.muted(c, "");
        targetNote.setGravity(Gravity.CENTER);
        result.addView(targetNote);
        row.addView(result, Ui.lp(0, Ui.MATCH, 1f));
        col.addView(row, Ui.lpm(c, Ui.MATCH, 0, 0, 12, 0, 12));
        ((LinearLayout.LayoutParams) row.getLayoutParams()).weight = 1f;

        macroDial = new Dial(c);
        macroDial.addColumn("CARBS", "%", 0, 100, 1, 0, split[FastingLogic.CARBS]);
        macroDial.addColumn("PROTEIN", "%", 0, 100, 1, 0, split[FastingLogic.PROTEIN]);
        macroDial.addColumn("FAT", "%", 0, 100, 1, 0, split[FastingLogic.FAT]);
        macroDial.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) {
                split[column] = (int) Math.round(value);
                FastingLogic.rebalance(split, column, order);
                for (int i = 0; i < split.length; i++) if (i != column) macroDial.setValue(i, split[i]);
                updateTargets(maintenance);
            }
        });
        col.addView(macroDial, Ui.lp(Ui.MATCH, 0, 1.3f));
        LinearLayout gramRow = Ui.hbox(c);
        for (int i = 0; i < grams.length; i++) {
            grams[i] = Ui.text(c, "", 15, R.color.text, true);
            grams[i].setGravity(Gravity.CENTER);
            gramRow.addView(grams[i], Ui.lp(0, Ui.WRAP, 1f));
        }
        col.addView(gramRow, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        updateTargets(maintenance);
    }

    /** Starts from the saved split, else from the current targets, else (first run) from the suggested macros. */
    private void initTargets() {
        if (split != null) return;
        if (prefs.hasMacroSplit()) {
            adjust = prefs.kcalAdjust();
            split = prefs.macroSplit();
        } else if (prefs.onboarded()) {
            double pct = prefs.kcalTarget() * 100.0 / form().kcal() - 100;
            adjust = Math.max(-ADJUST_MAX, Math.min(ADJUST_MAX, (int) Math.round(pct / 5) * 5));
            split = FastingLogic.macroSplit(prefs.proteinTarget(), prefs.carbsTarget(), prefs.fatTarget());
        } else {
            adjust = 0;
            int[] m = FastingLogic.macroTargets(form().kcal(), form().weightV());
            split = FastingLogic.macroSplit(m[0], m[1], m[2]);
        }
    }

    private void updateTargets(int maintenance) {
        int kcal = FastingLogic.adjustedKcal(maintenance, adjust);
        targetKcal.setText(Ui.n0(kcal));
        targetNote.setText(adjust == 0 ? "Maintenance" : Math.abs(adjust) + "% " + (adjust < 0 ? "deficit" : "surplus"));
        int[] g = FastingLogic.macroGrams(kcal, split);
        for (int i = 0; i < grams.length; i++) grams[i].setText(g[i] + " g");
    }

    private void saveTargets() {
        int kcal = FastingLogic.adjustedKcal(form().kcal(), adjust);
        int[] g = FastingLogic.macroGrams(kcal, split);
        prefs.setTargets(kcal, g[FastingLogic.PROTEIN], g[FastingLogic.CARBS], g[FastingLogic.FAT]);
        prefs.setMacroSplit(adjust, split);
    }

    // ---------- water target ----------

    private void water(LinearLayout col) {
        col.addView(title(c, "Water target"));
        col.addView(subtitle(c, "How much do you want to drink each day? You can change this any time."));
        Dial dial = new Dial(c);
        dial.addColumn("WATER", "ml", FastingLogic.WATER_MIN, FastingLogic.WATER_MAX, FastingLogic.WATER_STEP, 0, waterMl);
        dial.setFormat(0, new Dial.Format() {
            @Override
            public String of(double v) { return Ui.n0(v); }
        });
        dial.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) { waterMl = (int) Math.round(value); }
        });
        col.addView(dial, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    // ---------- workout target ----------

    private void workouts(LinearLayout col) {
        col.addView(title(c, "Workout target"));
        col.addView(subtitle(c, "How many workouts do you want to do each day? You can change this any time."));
        Dial dial = new Dial(c);
        dial.addColumn("WORKOUTS", "per day", FastingLogic.WORKOUT_MIN, FastingLogic.WORKOUT_MAX, 1, 0, workouts);
        dial.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) { workouts = (int) Math.round(value); }
        });
        col.addView(dial, Ui.lp(Ui.MATCH, Ui.WRAP));

        col.addView(SettingsScreen.ProfileForm.label(a, "Rest days", "No workout target on these days."));
        LinearLayout days = Ui.hbox(c);
        DayOfWeek first = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
        for (int i = 0; i < 7; i++) {
            DayOfWeek d = first.plus(i);
            final int bit = FastingLogic.restBit(d);
            final Choice day = Choice.dayToggle(c, d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    d.getDisplayName(TextStyle.FULL, Locale.getDefault()));
            day.setSelected((restDays & bit) != 0);
            day.onClick(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    restDays ^= bit;
                    day.setSelected((restDays & bit) != 0);
                }
            });
            if (i > 0) days.addView(Ui.hspace(c, 6));
            days.addView(day.view, Ui.lp(0, Ui.WRAP, 1f));
        }
        col.addView(days, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    /** Small spaced caps header, styled like a dial column label. */
    private TextView dialLabel(String s, boolean faded) {
        TextView t = Ui.text(c, s, 12, R.color.primary, true);
        t.setLetterSpacing(0.08f);
        t.setGravity(Gravity.CENTER);
        if (faded) t.setTextColor(Ui.alpha(Ui.col(c, R.color.text2), 0.6f));
        return t;
    }

    /** A big kcal number with a small unit after it, like a dial's selected value. */
    private LinearLayout amount(TextView num, int color) {
        LinearLayout r = Ui.hbox(c);
        r.setGravity(Gravity.CENTER);
        num.setTextColor(color);
        r.addView(num);
        TextView unit = Ui.text(c, "kcal", 14, R.color.text, true);
        unit.setTextColor(color);
        unit.setPadding(Ui.dp(c, 4), 0, 0, 0);
        r.addView(unit);
        return r;
    }

    // ---------- navigation ----------

    private void next() {
        if (steps[step] == DISCLAIMER && !accepted) return;
        if (step < steps.length - 1) {
            step++;
            direction = 1;
            a.rebuild();
            return;
        }
        if (settings && steps[step] == WATER) {
            prefs.setWaterTarget(waterMl);
            a.pop();
            a.snack("Water target set to " + Ui.n0(waterMl) + " ml", null);
            return;
        }
        if (settings && steps[step] == WORKOUTS) {
            prefs.setWorkoutTarget(workouts, restDays);
            a.pop();
            a.snack("Workout target set to " + workouts + FastingLogic.sessions(workouts), null);
            return;
        }
        form().saveProfile();
        if (settings) {
            boolean retarget = steps[step] == TARGETS || prefs.hasMacroSplit();
            // A new profile changes maintenance; keep the saved surplus/deficit and split on top of it.
            if (retarget) {
                initTargets();
                saveTargets();
            }
            a.pop();
            a.snack(retarget ? "Target set to " + Ui.n0(prefs.kcalTarget()) + " kcal" : "Profile saved", null);
            return;
        }
        saveTargets();
        prefs.setWaterTarget(waterMl);
        prefs.setWorkoutTarget(workouts, restDays);
        prefs.setPlan(planId, customHours);
        prefs.setOnboarded(true);
        a.finishOnboarding();
    }

    void back() {
        if (step == 0) {
            if (settings) a.pop();
            return;
        }
        if (steps[step] == ABOUT && form != null) form.saveProfile();
        step--;
        direction = -1;
        a.rebuild();
    }
}
