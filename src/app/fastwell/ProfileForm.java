package app.fastwell;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Sex toggle, age/height dial, weight dial and activity cards, with a live Mifflin-St Jeor estimate. */
final class ProfileForm {
    /** Shorter than a dial's own height, so the two stacked dials leave room to scroll the page. */
    private static final int DIAL_DP = 184;

    final LinearLayout view;
    final TextView estimate;
    private final Prefs prefs;
    private final Db db;
    private final Dial dial;
    private final WeightDial weightDial;
    private final int colAge, colHeight;
    private final Choice male, female;
    private final List<Choice> sexes = new ArrayList<Choice>();
    private final List<Choice> levels = new ArrayList<Choice>();
    private boolean isMale;
    private int activity;

    ProfileForm(MainActivity c, Prefs prefs) {
        this.prefs = prefs;
        db = Db.get(c);
        isMale = prefs.male();
        activity = Math.max(0, Math.min(FastingLogic.ACTIVITY.length - 1, prefs.activity()));
        view = Ui.vbox(c);

        view.addView(label(c, "Sex", "Used by the calorie formula."));
        LinearLayout sexRow = Ui.hbox(c);
        male = Choice.toggle(c, "Male");
        female = Choice.toggle(c, "Female");
        sexes.add(male);
        sexes.add(female);
        sexRow.addView(male.view, Ui.lp(0, Ui.WRAP, 1f));
        sexRow.addView(Ui.hspace(c, 10));
        sexRow.addView(female.view, Ui.lp(0, Ui.WRAP, 1f));
        view.addView(sexRow, Ui.lp(Ui.MATCH, Ui.WRAP));
        Choice.select(sexes, isMale ? male : female);
        male.onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) { isMale = true; Choice.select(sexes, male); update(); }
        });
        female.onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) { isMale = false; Choice.select(sexes, female); update(); }
        });

        view.addView(label(c, "Age and height", "Drag each number up or down."));
        dial = new Dial(c);
        colAge = dial.addColumn("AGE", "yrs", 14, 100, 1, 0, prefs.age());
        colHeight = dial.addColumn("HEIGHT", "cm", 120, 230, 1, 0, prefs.heightCm());
        dial.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) { update(); }
        });
        view.addView(dial, Ui.lp(Ui.MATCH, Ui.dp(c, DIAL_DP)));

        view.addView(label(c, "Weight", null));
        weightDial = new WeightDial(c, prefs.weightKg());
        weightDial.onChange(new Runnable() {
            @Override
            public void run() { update(); }
        });
        view.addView(weightDial.view, Ui.lp(Ui.MATCH, Ui.dp(c, DIAL_DP)));

        view.addView(label(c, "Activity level", null));
        for (int i = 0; i < FastingLogic.ACTIVITY.length; i++) {
            String full = FastingLogic.ACTIVITY[i];
            int p = full.indexOf(" (");
            String title = p > 0 ? full.substring(0, p) : full;
            String note = p > 0 ? full.substring(p + 2, full.length() - 1) : null;
            if (note != null) note = Character.toUpperCase(note.charAt(0)) + note.substring(1);
            final Choice ch = Choice.card(c, Choice.levelBars(c, i), title, note);
            final int level = i;
            ch.onClick(new View.OnClickListener() {
                @Override
                public void onClick(View v) { activity = level; Choice.select(levels, ch); update(); }
            });
            levels.add(ch);
            view.addView(ch.view, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        }
        Choice.select(levels, levels.get(activity));

        LinearLayout est = Ui.vbox(c);
        est.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
        est.setBackground(Ui.round(Ui.alpha(Ui.col(c, R.color.primary), Ui.night(c) ? 0.2f : 0.08f), Ui.dp(c, 14)));
        est.addView(Ui.muted(c, "Maintenance calories"));
        estimate = Ui.big(c, "", 24, R.color.text);
        est.addView(estimate);
        view.addView(est, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 6, 0, 4));
        update();
    }

    static View label(MainActivity c, String title, String sub) {
        LinearLayout l = Ui.vbox(c);
        l.setPadding(Ui.dp(c, 4), Ui.dp(c, 16), 0, Ui.dp(c, 8));
        l.addView(Ui.text(c, title, 15, R.color.text, true));
        if (sub != null) l.addView(Ui.muted(c, sub));
        return l;
    }

    int ageV() { return (int) Math.round(dial.value(colAge)); }
    int heightV() { return (int) Math.round(dial.value(colHeight)); }
    float weightV() { return (float) weightDial.kg(); }
    int activityV() { return activity; }

    int kcal() { return FastingLogic.maintenanceKcal(isMale, ageV(), heightV(), weightV(), activityV()); }

    void update() { estimate.setText(Ui.n0(kcal()) + " kcal / day"); }

    /** Saves the profile, logging a changed or first weight for today; targets are set on the daily target step. */
    void saveProfile() {
        float kg = weightV();
        if (kg != prefs.weightKg() || db.latestWeight() == null) db.setWeight(Db.today(), kg);
        prefs.setProfile(isMale, ageV(), heightV(), kg, activityV());
    }
}
