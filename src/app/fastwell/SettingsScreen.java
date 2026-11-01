package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.time.temporal.WeekFields;
import java.util.Locale;

/** Targets, profile, plan, notifications, backup and data deletion. */
final class SettingsScreen extends Screen {

    SettingsScreen(MainActivity a) { super(a); }

    @Override
    int navTab() { return MainActivity.TAB_TODAY; }

    @Override
    View build() {
        LinearLayout col = Ui.column(c, 32);
        col.addView(backHeader("Settings"));

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
        t.addView(row("Goal weight", prefs.goalKg() > 0 ? FastingLogic.kgText(prefs.goalKg()) : "Not set", new Runnable() {
            @Override
            public void run() { a.push(new GoalWeightScreen(a)); }
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
        ab.addView(Ui.text(c, "Fastwell " + CrashLog.version, 15, R.color.text, true));
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
                        .setMessage("Every fast, meal, workout and weight log will be permanently removed, along with your profile, targets and settings. "
                                + "Export a backup first if you might want it back.")
                        .setPositiveButton("Delete everything", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                a.deleteAllData();
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
}
