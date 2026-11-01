package app.fastwell;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Meal pills in one rounded bar, like the date strip; used to log and to edit a food entry. */
final class MealBar {
    private MealBar() {}

    /** Called with the meal whose pill was tapped. */
    interface Picked {
        void picked(int meal);
    }

    /** The first {@code count} meals as pills; fills {@code pills}. */
    static View create(Context c, int count, List<TextView> pills, final Picked listener) {
        LinearLayout bar = Ui.hbox(c);
        int p = Ui.dp(c, 4);
        bar.setPadding(p, p, p, p);
        bar.setBackground(Ui.round(Ui.col(c, R.color.surface), Ui.dp(c, 24), Ui.col(c, R.color.outline), Ui.night(c) ? 0 : Ui.dp(c, 1)));
        pills.clear();
        for (int i = 0; i < count; i++) {
            final int m = i;
            TextView pill = Ui.text(c, FastingLogic.MEALS[i], 13, R.color.text2, true);
            pill.setGravity(Gravity.CENTER);
            pill.setSingleLine(true);
            pill.setPadding(Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10));
            pill.setClickable(true);
            pill.setFocusable(true);
            pill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { listener.picked(m); }
            });
            pills.add(pill);
            bar.addView(pill, Ui.lp(Ui.WRAP, Ui.WRAP, 1f));
        }
        LinearLayout.LayoutParams lp = Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 4, 0, 16);
        bar.setLayoutParams(lp);
        return bar;
    }

    /** Restyles the meal pills in place. */
    static void show(Context c, List<TextView> pills, int meal) {
        for (int i = 0; i < pills.size(); i++) {
            TextView pill = pills.get(i);
            boolean on = i == meal;
            int fill = on ? Ui.alpha(Ui.col(c, R.color.eat), Ui.night(c) ? 0.22f : 0.14f) : 0;
            pill.setBackground(Ui.ripple(c, Ui.round(fill, Ui.dp(c, 20)), Ui.dp(c, 20)));
            pill.setTextColor(Ui.col(c, on ? R.color.eat : R.color.text2));
            pill.setContentDescription(FastingLogic.MEALS[i] + (on ? ", selected" : ""));
        }
    }
}
