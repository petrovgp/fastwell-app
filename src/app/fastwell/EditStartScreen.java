package app.fastwell;

import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Full-screen editor for a running fast's start: a day dial and a time dial that share the screen without scrolling. */
final class EditStartScreen extends Screen {
    /** How far back the day dial reaches (further if the fast already started earlier). */
    static final int DAYS_BACK = 7;

    private final Db.Fast fast;
    private final long prevEnd;
    private final LocalDate firstDay;
    private final int dayCount;
    private long startAt;

    private Dial dayDial, timeDial;
    private int hourCol, minCol, ampmCol = -1;
    private TextView startedVal, goalVal, status;
    private Button save;

    EditStartScreen(MainActivity a, Db.Fast f) {
        super(a);
        fast = f;
        startAt = f.startAt;
        Db.Fast last = db.lastCompletedFast();
        prevEnd = last != null ? last.endAt : 0;
        LocalDate today = LocalDate.now();
        LocalDate first = today.minusDays(DAYS_BACK);
        LocalDate startDay = day(f.startAt);
        if (startDay.isBefore(first)) first = startDay;
        if (prevEnd > 0 && day(prevEnd).isAfter(first) && !day(prevEnd).isAfter(startDay)) first = day(prevEnd);
        firstDay = first;
        dayCount = (int) ChronoUnit.DAYS.between(firstDay, today) + 1;
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

        LinearLayout col = Ui.vbox(c);
        col.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        col.addView(OnboardingScreen.title(c, "Edit start time"));
        col.addView(summaryCard());

        LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(startAt), ZoneId.systemDefault());
        Dial.Listener changed = new Dial.Listener() {
            @Override
            public void onChange(int column, double value) { readDials(); }
        };

        dayDial = new Dial(c);
        int dayCol = dayDial.addColumn("DAY", "", 0, dayCount - 1, 1, 0, ChronoUnit.DAYS.between(firstDay, t.toLocalDate()));
        dayDial.setFormat(dayCol, new Dial.Format() {
            @Override
            public String of(double v) { return Ui.dayLabel(firstDay.plusDays((long) v)); }
        });
        dayDial.setListener(changed);
        col.addView(dayDial, Ui.lpm(c, Ui.MATCH, 0, 0, 0, 0, 12));
        ((LinearLayout.LayoutParams) dayDial.getLayoutParams()).weight = 1f;

        timeDial = new Dial(c);
        Dial.Format twoDigits = new Dial.Format() {
            @Override
            public String of(double v) { return String.format(Locale.US, "%02d", (int) v); }
        };
        if (DateFormat.is24HourFormat(c)) {
            hourCol = timeDial.addColumn("HOUR", "", 0, 23, 1, 0, t.getHour());
            timeDial.setFormat(hourCol, twoDigits);
        } else {
            int h12 = t.getHour() % 12;
            hourCol = timeDial.addColumn("HOUR", "", 1, 12, 1, 0, h12 == 0 ? 12 : h12);
        }
        minCol = timeDial.addColumn("MIN", "", 0, 59, 1, 0, t.getMinute());
        timeDial.setFormat(minCol, twoDigits);
        if (!DateFormat.is24HourFormat(c)) {
            ampmCol = timeDial.addColumn("", "", 0, 1, 1, 0, t.getHour() >= 12 ? 1 : 0);
            timeDial.setFormat(ampmCol, new Dial.Format() {
                @Override
                public String of(double v) { return v >= 1 ? "PM" : "AM"; }
            });
        }
        timeDial.setListener(changed);
        col.addView(timeDial, Ui.lp(Ui.MATCH, 0, 1f));
        root.addView(col, Ui.lp(Ui.MATCH, 0, 1f));

        LinearLayout bottom = Ui.vbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        save = Ui.primary(c, "Save start time", R.color.primary);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (problem() != null) return;
                fast.startAt = startAt;
                db.updateFast(fast);
                Notifier.refresh(c);
                a.pop();
                a.snack("Start moved to " + Ui.dateTime(c, startAt), null);
            }
        });
        bottom.addView(save, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));
        update();
        return root;
    }

    private View summaryCard() {
        LinearLayout card = Ui.card(c);
        LinearLayout row = Ui.hbox(c);
        LinearLayout started = Ui.vbox(c);
        started.addView(Ui.muted(c, "Started"));
        startedVal = Ui.text(c, "", 16, R.color.text, true);
        started.addView(startedVal);
        row.addView(started, Ui.lp(0, Ui.WRAP, 1f));
        LinearLayout goal = Ui.vbox(c);
        goal.addView(Ui.muted(c, "Goal end"));
        goalVal = Ui.text(c, "", 16, R.color.text, true);
        goal.addView(goalVal);
        row.addView(goal, Ui.lp(0, Ui.WRAP, 1f));
        card.addView(row);
        status = Ui.text(c, "", 13, R.color.primary, true);
        status.setPadding(0, Ui.dp(c, 8), 0, 0);
        card.addView(status);
        return card;
    }

    private void readDials() {
        LocalDate d = firstDay.plusDays((long) dayDial.value(0));
        int h = (int) timeDial.value(hourCol);
        if (ampmCol >= 0) h = h % 12 + (timeDial.value(ampmCol) >= 1 ? 12 : 0);
        int m = (int) timeDial.value(minCol);
        startAt = LocalDateTime.of(d.getYear(), d.getMonthValue(), d.getDayOfMonth(), h, m)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        update();
    }

    /** Why the picked time can't be saved, or null if it can. */
    private String problem() {
        if (startAt > System.currentTimeMillis()) return "Start time can't be in the future";
        if (prevEnd > 0 && startAt < prevEnd) return "That overlaps your previous fast, which ended " + Ui.dateTime(c, prevEnd);
        return null;
    }

    private void update() {
        if (startedVal == null) return;
        startedVal.setText(Ui.dateTime(c, startAt));
        goalVal.setText(Ui.dateTime(c, startAt + fast.goalMs()));
        // One status line: the reason it can't be saved, otherwise how long you've fasted.
        String p = problem();
        status.setText(p != null ? p : "Fasting for " + FastingLogic.dur(System.currentTimeMillis() - startAt)
                + " of your " + FastingLogic.dur(fast.goalMs()) + " goal");
        status.setTextColor(Ui.col(c, p != null ? R.color.error : R.color.primary));
        save.setEnabled(p == null);
        save.setAlpha(p == null ? 1f : 0.4f);
    }

    @Override
    void tick() { update(); }

    private static LocalDate day(long t) {
        return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
