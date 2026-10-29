package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Custom fasting plan pop-up: fasting hours (draggable) and eating hours (24 - fasting, read-only). */
final class HourDial {
    private HourDial() {}

    static final int MIN = 1, MAX = 23;

    interface IntCb { void on(int fastHours); }

    static void show(final MainActivity a, int initialHours, final IntCb onSet) {
        LinearLayout body = Ui.dialogBody(a);
        TextView hint = Ui.muted(a, "Drag the fasting hours up or down. Eating time fills the rest of the 24 hours.");
        hint.setPadding(Ui.dp(a, 4), 0, 0, Ui.dp(a, 12));
        body.addView(hint);

        final Dial dial = new Dial(a);
        final int fast = dial.addColumn("FASTING", "h", MIN, MAX, 1, 0, Math.max(MIN, Math.min(MAX, initialHours)));
        dial.addDerived("EATING", "h", fast, 0, new Dial.Derive() {
            @Override
            public double of(double f) { return 24 - f; }
        });
        body.addView(dial, Ui.lp(Ui.MATCH, Ui.WRAP));

        final TextView summary = Ui.text(a, "", 15, R.color.text, true);
        summary.setPadding(Ui.dp(a, 4), Ui.dp(a, 14), 0, Ui.dp(a, 6));
        body.addView(summary);
        final Views.Bar split = new Views.Bar(a, Ui.col(a, R.color.primary), 0);
        split.setTrack(Ui.col(a, R.color.peach));
        body.addView(split, Ui.lpm(a, Ui.MATCH, Ui.dp(a, 10), 0, 0, 0, 4));
        LinearLayout ends = Ui.hbox(a);
        ends.addView(Ui.text(a, "0h", 11, R.color.text2, false), Ui.lp(0, Ui.WRAP, 1f));
        ends.addView(Ui.text(a, "24h", 11, R.color.text2, false));
        body.addView(ends);

        final Runnable upd = new Runnable() {
            @Override
            public void run() {
                int f = (int) dial.value(fast);
                summary.setText(f + "h fasting  ·  " + (24 - f) + "h eating   (" + f + ":" + (24 - f) + ")");
                split.set(f / 24f, Ui.col(a, R.color.primary));
            }
        };
        dial.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) { upd.run(); }
        });
        upd.run();

        new AlertDialog.Builder(a)
                .setTitle("Custom plan")
                .setView(body)
                .setPositiveButton("Set", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) { onSet.on((int) dial.value(fast)); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
