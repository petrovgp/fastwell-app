package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.text.Editable;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Targets, profile, plan, notifications, backup and data deletion. */
final class SettingsScreen extends Screen {

    SettingsScreen(MainActivity a) { super(a); }

    @Override
    int navTab() { return MainActivity.TAB_TODAY; }

    @Override
    View build() {
        LinearLayout col = Ui.column(c, 32);
        col.addView(FastHistoryScreen.backHeader(a, "Settings"));

        col.addView(Ui.sectionTitle(c, "Targets"));
        LinearLayout t = Ui.card(c);
        t.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        t.addView(row("Daily calories and macros", Ui.n0(prefs.kcalTarget()) + " kcal  -  P " + prefs.proteinTarget()
                + " g, C " + prefs.carbsTarget() + " g, F " + prefs.fatTarget() + " g", new Runnable() {
            @Override
            public void run() { a.push(OnboardingScreen.fromSettings(a, OnboardingScreen.TARGETS)); }
        }));
        t.addView(Ui.divider(c));
        t.addView(row("Daily water", Ui.n0(prefs.waterTarget()) + " ml", new Runnable() {
            @Override
            public void run() { a.push(OnboardingScreen.fromSettings(a, OnboardingScreen.WATER)); }
        }));
        t.addView(Ui.divider(c));
        String rest = FastingLogic.restDayNames(prefs.restDays(), WeekFields.of(Locale.getDefault()).getFirstDayOfWeek(), Locale.getDefault());
        t.addView(row("Daily workouts", prefs.workoutTarget() + FastingLogic.sessions(prefs.workoutTarget())
                + (rest.isEmpty() ? "" : "  -  Rest: " + rest), new Runnable() {
            @Override
            public void run() { a.push(OnboardingScreen.fromSettings(a, OnboardingScreen.WORKOUTS)); }
        }));
        t.addView(Ui.divider(c));
        t.addView(row("Profile",(prefs.male() ? "Male" : "Female") + ", " + prefs.age() + " y, " + prefs.heightCm() + " cm, "
                + Ui.n1(prefs.weightKg()) + " kg", new Runnable() {
            @Override
            public void run() { a.push(OnboardingScreen.fromSettings(a, OnboardingScreen.ABOUT)); }
        }));
        col.addView(t);

        col.addView(Ui.sectionTitle(c, "Fasting"));
        LinearLayout f = Ui.card(c);
        f.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        f.addView(row("Fasting plan", prefs.plan().name.replace("  ", " "), new Runnable() {
            @Override
            public void run() { a.push(new PlanScreen(a)); }
        }));
        col.addView(f);

        col.addView(Ui.sectionTitle(c, "Notifications"));
        LinearLayout n = Ui.card(c);
        n.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        n.addView(toggle("Timer in notification bar", "Live fasting timer while you fast", prefs.ongoingNotif(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean on) {
                        prefs.setOngoingNotif(on);
                        Notifier.refresh(c);
                    }
                }));
        n.addView(Ui.divider(c));
        n.addView(toggle("Goal reached reminder", "Notifies you when you hit your fasting goal", prefs.goalNotif(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean on) {
                        prefs.setGoalNotif(on);
                        Notifier.refresh(c);
                    }
                }));
        n.addView(Ui.divider(c));
        n.addView(row("System notification settings", "Permissions, sound and channels", new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
                a.startActivity(i);
            }
        }));
        col.addView(n);

        col.addView(Ui.sectionTitle(c, "Backup"));
        LinearLayout b = Ui.card(c);
        b.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        b.addView(row("Export backup", "Save all your data as a JSON file", new Runnable() {
            @Override
            public void run() { a.startExport(); }
        }));
        b.addView(Ui.divider(c));
        b.addView(row("Import backup", "Restore from a Fastwell JSON file", new Runnable() {
            @Override
            public void run() { a.startImport(); }
        }));
        col.addView(b);

        col.addView(Ui.sectionTitle(c, "About"));
        LinearLayout ab = Ui.card(c);
        ab.addView(Ui.text(c, "Fastwell 1.0.0", 15, R.color.text, true));
        ab.addView(Ui.muted(c, "All data stays on this phone. Food search uses Open Food Facts (openfoodfacts.org)."));
        TextView disc = Ui.muted(c, "Fastwell is not medical advice. Talk to a doctor before fasting if you are pregnant or "
                + "breastfeeding, have diabetes, take medication, are under 18, or have a history of eating disorders.");
        disc.setPadding(0, Ui.dp(c, 8), 0, 0);
        ab.addView(disc);
        col.addView(ab);

        LinearLayout danger = Ui.card(c);
        danger.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        TextView del = Ui.text(c, "Delete all data", 15, R.color.error, true);
        del.setPadding(Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14));
        Ui.clickable(del, 8);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(c)
                        .setTitle("Delete all data?")
                        .setMessage("Every fast, meal and workout will be permanently removed. Export a backup first if you might want it back.")
                        .setPositiveButton("Delete everything", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                db.deleteAll();
                                Notifier.refresh(c);
                                a.refresh();
                                a.snack("All data deleted", null);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        danger.addView(del);
        col.addView(danger);
        return Ui.scroll(c, col);
    }

    private View row(String title, String value, final Runnable onClick) {
        LinearLayout r = Ui.vbox(c);
        r.setPadding(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12));
        r.addView(Ui.text(c, title, 15, R.color.text, false));
        r.addView(Ui.muted(c, value));
        Ui.clickable(r, 8);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onClick.run(); }
        });
        return r;
    }

    private View toggle(String title, String sub, boolean on, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = Ui.hbox(c);
        r.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
        LinearLayout tx = Ui.vbox(c);
        tx.addView(Ui.text(c, title, 15, R.color.text, false));
        tx.addView(Ui.muted(c, sub));
        r.addView(tx, Ui.lp(0, Ui.WRAP, 1f));
        Switch s = new Switch(c);
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        r.addView(s);
        return r;
    }

    /** Sex toggle, age/height dial, weight dial and activity cards, with a live Mifflin-St Jeor estimate. */
    static final class ProfileForm {
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
}
