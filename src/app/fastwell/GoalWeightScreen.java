package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen goal weight, opened from Settings, showing how far it is from the current weight. */
final class GoalWeightScreen extends Screen {
    private final double current;
    /** Kept here rather than in the dial, so a rebuild (e.g. on resume) keeps it. */
    private double kg;

    private WeightDial dial;
    private TextView note;

    GoalWeightScreen(MainActivity a) {
        super(a);
        current = FastingLogic.tenth(prefs.weightKg());
        kg = prefs.goalKg() > 0 ? FastingLogic.tenth(prefs.goalKg()) : current;
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        LinearLayout header = FastHistoryScreen.backHeader(a, "Goal weight");
        header.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0);
        root.addView(header);

        LinearLayout col = Ui.column(c, 24);
        dial = new WeightDial(c, kg);
        dial.onChange(new Runnable() {
            @Override
            public void run() {
                kg = dial.kg();
                showChange();
            }
        });
        col.addView(dial.view, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        note = Ui.muted(c, "");
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(c, 12), 0, 0);
        col.addView(note, Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(Ui.scroll(c, col), Ui.lp(Ui.MATCH, 0, 1f));

        LinearLayout bottom = Ui.vbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        if (prefs.goalKg() > 0) {
            Button remove = Ui.outline(c, "Remove goal", R.color.text2);
            remove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.clearGoal();
                    a.pop();
                    a.snack("Goal removed", null);
                }
            });
            bottom.addView(remove, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 48), 0, 0, 0, 8));
        }
        Button save = Ui.primary(c, "Save", R.color.primary);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setGoal((float) kg, (float) current);
                a.pop();
                a.snack("Goal set to " + FastingLogic.kgText(kg), null);
            }
        });
        bottom.addView(save, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));

        showChange();
        return root;
    }

    private void showChange() {
        note.setText(changeNote(current, kg));
    }

    /** How a goal compares with the current weight. */
    static String changeNote(double current, double goal) {
        String change = FastingLogic.weightChange(current, goal);
        return Character.toUpperCase(change.charAt(0)) + change.substring(1) + " from your current weight of " + FastingLogic.kgText(current);
    }
}
