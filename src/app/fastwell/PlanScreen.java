package app.fastwell;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen fasting plan picker, laid out like the onboarding plan step. Save is pinned at the bottom. */
final class PlanScreen extends Screen {
    private String planId;
    private int customHours;

    PlanScreen(MainActivity a) {
        super(a);
        planId = prefs.planId();
        customHours = prefs.customHours();
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        LinearLayout bar = Ui.hbox(c);
        bar.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 4));
        ImageView back = Ui.iconButton(c, R.drawable.ic_left, R.color.text, "Back");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.handleBack(); }
        });
        bar.addView(back);
        root.addView(bar);

        LinearLayout col = Ui.column(c, 24);
        col.addView(OnboardingScreen.title(c, "Your fasting plan"));
        col.addView(OnboardingScreen.subtitle(c, "How long do you want to fast each day? You can change this any time."));
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
        root.addView(Ui.scroll(c, col), Ui.lp(Ui.MATCH, 0, 1f));

        LinearLayout bottom = Ui.vbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        Button save = Ui.primary(c, "Save plan", R.color.primary);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.pop();
                apply(a, planId, customHours);
            }
        });
        bottom.addView(save, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));
        return root;
    }

    /** Saves the plan and moves a running fast's goal to it. */
    static void apply(MainActivity a, String id, int customHours) {
        a.prefs.setPlan(id, customHours);
        FastingLogic.Plan p = a.prefs.plan();
        Db.Fast f = a.db.activeFast();
        if (f != null) {
            f.goalMin = p.goalMinutes();
            f.plan = p.id;
            a.db.updateFast(f);
        }
        Notifier.refresh(a);
        a.refresh();
        a.snack("Plan set to " + p.ratio(), null);
    }
}
