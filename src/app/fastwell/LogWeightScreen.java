package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen weight log for the selected day, opened from the Food screen. Starts from that day's log or the
 * last one before it, and shows how the dialed weight compares with that last one.
 */
final class LogWeightScreen extends Screen {
    private final String day;
    /** The last weight logged before this day, or null. */
    private final Double previous;
    /** Kept here rather than in the dial, so a rebuild (e.g. on resume) keeps it. */
    private double kg;

    private WeightDial dial;
    private TextView note;

    LogWeightScreen(MainActivity a) {
        super(a);
        day = a.selectedDay.toString();
        previous = db.weightBefore(day);
        Double logged = db.weightOn(day);
        kg = logged != null ? logged : previous != null ? previous : FastingLogic.tenth(prefs.weightKg());
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        LinearLayout header = FastHistoryScreen.backHeader(a, "Log weight");
        header.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0);
        header.addView(Ui.chip(c, Ui.dayLabel(a.selectedDay), R.color.eat));
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
        Button save = Ui.primary(c, "Save", R.color.eat);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { save(); }
        });
        bottom.addView(save, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));

        showChange();
        return root;
    }

    /** Gray when nothing changed, green when weight is lost and red when it is gained. */
    private void showChange() {
        if (previous == null) {
            note.setText("First weight log");
            note.setTextColor(Ui.col(c, R.color.text2));
            return;
        }
        String change = FastingLogic.weightChange(previous, kg);
        note.setText(Character.toUpperCase(change.charAt(0)) + change.substring(1) + " from last log");
        double d = FastingLogic.tenth(kg - previous);
        note.setTextColor(Ui.col(c, d == 0 ? R.color.text2 : d < 0 ? R.color.success : R.color.error));
    }

    private void save() {
        db.setWeight(day, kg);
        // A log for an earlier day doesn't replace the current weight.
        prefs.setWeightKg(db.latestWeight().floatValue());
        a.pop();
        a.snack("Logged " + FastingLogic.kgText(kg), null);
    }
}
