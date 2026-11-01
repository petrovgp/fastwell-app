package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;

/** Date strip used by Food and Workouts: previous day, tap for calendar, next day. */
final class DateStrip {
    private DateStrip() {}

    /** The day label slides in from the side the day moved towards. */
    static View create(final MainActivity a) {
        LinearLayout row = Ui.hbox(a);
        row.setBackground(Ui.round(Ui.col(a, R.color.surface), Ui.dp(a, 24), Ui.col(a, R.color.outline), Ui.night(a) ? 0 : Ui.dp(a, 1)));
        row.setLayoutParams(Ui.lpm(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        ImageView prev = Ui.iconButton(a, R.drawable.ic_left, R.color.text, "Previous day");
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.goToDay(a.selectedDay.minusDays(1)); }
        });
        TextView label = Ui.text(a, Ui.dayLabel(a.selectedDay), 16, R.color.text, true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));
        Ui.clickable(label, 20);
        label.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.push(a.currentScreen().dayPicker()); }
        });
        ImageView next = Ui.iconButton(a, R.drawable.ic_right, R.color.text, "Next day");
        final boolean isToday = !a.selectedDay.isBefore(LocalDate.now());
        next.setAlpha(isToday ? 0.3f : 1f);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isToday) return;
                a.goToDay(a.selectedDay.plusDays(1));
            }
        });
        row.addView(prev);
        row.addView(label, Ui.lp(0, Ui.WRAP, 1f));
        row.addView(next);
        Ui.slideIn(label, a.daySlide, 24);
        return row;
    }
}
