package app.fastwell;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** The Routine card on Log strength: empty with two ways to add one, or filled with its exercises. */
final class RoutineCard {
    interface Actions {
        void choose();

        void create();

        void edit();

        void saveAs();

        void update();
    }

    private RoutineCard() {}

    /** A null updateName hides the Update button. */
    static View create(Context c, String title, List<FastingLogic.Lift> lifts, String updateName, final Actions actions) {
        LinearLayout card = Ui.card(c);
        if (lifts.isEmpty()) {
            card.addView(Ui.muted(c, "No routine added"));
            LinearLayout row = Ui.hbox(c);
            row.addView(button(Ui.outline(c, "Choose saved", R.color.train), new Runnable() {
                @Override
                public void run() { actions.choose(); }
            }), Ui.lp(0, Ui.WRAP, 1f));
            row.addView(Ui.hspace(c, 10));
            row.addView(button(Ui.outline(c, "New routine", R.color.train), new Runnable() {
                @Override
                public void run() { actions.create(); }
            }), Ui.lp(0, Ui.WRAP, 1f));
            card.addView(row, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
            return card;
        }
        card.addView(Ui.text(c, title, 16, R.color.text, true));
        card.addView(Ui.muted(c, FastingLogic.routineSummary(lifts)));
        for (FastingLogic.Lift l : lifts) {
            LinearLayout line = Ui.hbox(c);
            TextView name = Ui.text(c, l.name, 15, R.color.text, false);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(name, Ui.lp(0, Ui.WRAP, 1f));
            line.addView(Ui.hspace(c, 8));
            line.addView(Ui.muted(c, FastingLogic.liftLine(l)));
            card.addView(line, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        }
        LinearLayout row = Ui.hbox(c);
        String[] labels = {"Edit", "Change", "Save as"};
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            if (i > 0) row.addView(Ui.hspace(c, 8));
            row.addView(button(Ui.outline(c, labels[i], R.color.train), new Runnable() {
                @Override
                public void run() {
                    if (which == 0) actions.edit();
                    else if (which == 1) actions.choose();
                    else actions.saveAs();
                }
            }), Ui.lp(0, Ui.WRAP, 1f));
        }
        card.addView(row, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        if (updateName != null) {
            Button update = button(Ui.tonal(c, "Update " + updateName, R.color.train), new Runnable() {
                @Override
                public void run() { actions.update(); }
            });
            card.addView(update, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        }
        return card;
    }

    private static Button button(Button b, final Runnable r) {
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { r.run(); }
        });
        return b;
    }
}
