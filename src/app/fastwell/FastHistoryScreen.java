package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Completed fasts with streaks, stats and a month calendar; tap a day to edit or delete its fast (several on one day are
 * listed on DayFastsScreen first), or add a past one; both open PastFastScreen.
 */
final class FastHistoryScreen extends Screen {

    private YearMonth month = YearMonth.now();

    FastHistoryScreen(MainActivity a) { super(a); }

    @Override
    int navTab() { return MainActivity.TAB_FAST; }

    @Override
    View build() {
        // Fixed, non-scrolling layout: header, compact stats, then the calendar takes whatever height is left.
        LinearLayout col = Ui.column(c, 12);
        LinearLayout header = backHeader(a, "Fasting history");
        Button add = Ui.tonal(c, "+  Add", R.color.primary);
        add.setContentDescription("Add a past fast");
        add.setMinHeight(Ui.dp(c, 36));
        add.setMinimumHeight(Ui.dp(c, 36));
        add.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.push(new PastFastScreen(a)); }
        });
        header.addView(add, Ui.lp(Ui.WRAP, Ui.dp(c, 36)));
        col.addView(header);

        List<Db.Fast> fasts = db.completedFasts(500);
        List<long[]> marks = new ArrayList<long[]>();
        long total = 0;
        int met = 0;
        for (Db.Fast f : fasts) {
            marks.add(new long[] {f.endAt, f.met() ? 1 : 0});
            total += f.endAt - f.startAt;
            if (f.met()) met++;
        }
        int[] st = FastingLogic.streaks(marks, LocalDate.now(), ZoneId.systemDefault());

        LinearLayout stats = Ui.card(c);
        int pad = Ui.dp(c, 12);
        stats.setPadding(pad, Ui.dp(c, 8), pad, Ui.dp(c, 8));
        LinearLayout r1 = Ui.hbox(c);
        r1.addView(stat("Current streak", st[0] + (st[0] == 1 ? " day" : " days")), Ui.lp(0, Ui.WRAP, 1f));
        r1.addView(stat("Best streak", st[1] + (st[1] == 1 ? " day" : " days")), Ui.lp(0, Ui.WRAP, 1f));
        stats.addView(r1);
        View div = Ui.divider(c);
        ((LinearLayout.LayoutParams) div.getLayoutParams()).setMargins(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        stats.addView(div);
        LinearLayout r2 = Ui.hbox(c);
        r2.addView(stat("Fasts", String.valueOf(fasts.size())), Ui.lp(0, Ui.WRAP, 1f));
        r2.addView(stat("Goal met", fasts.isEmpty() ? "-" : Math.round(100.0 * met / fasts.size()) + "%"), Ui.lp(0, Ui.WRAP, 1f));
        r2.addView(stat("Average", fasts.isEmpty() ? "-" : FastingLogic.dur(total / fasts.size())), Ui.lp(0, Ui.WRAP, 1f));
        stats.addView(r2);
        col.addView(stats);

        TextView calTitle = Ui.sectionTitle(c, "Calendar");
        calTitle.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), 0, Ui.dp(c, 6));
        col.addView(calTitle);
        col.addView(calendar(fasts), Ui.lp(Ui.MATCH, 0, 1f));
        return col;
    }

    static LinearLayout backHeader(final MainActivity a, String title) {
        LinearLayout h = Ui.hbox(a);
        h.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        ImageView back = Ui.iconButton(a, R.drawable.ic_left, R.color.text, "Back");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.handleBack(); }
        });
        h.addView(back);
        TextView t = Ui.text(a, title, 22, R.color.text, true);
        t.setPadding(Ui.dp(a, 4), 0, 0, 0);
        h.addView(t, Ui.lp(0, Ui.WRAP, 1f));
        return h;
    }

    private View stat(String label, String value) {
        LinearLayout v = Ui.vbox(c);
        v.addView(Ui.muted(c, label));
        v.addView(Ui.big(c, value, 18, R.color.text));
        return v;
    }

    /** Month grid; each day shows a green check if a fast ending that day met its goal, yellow if it ended early. */
    private View calendar(List<Db.Fast> fasts) {
        ZoneId zone = ZoneId.systemDefault();
        final Map<LocalDate, List<Db.Fast>> byDay = new HashMap<LocalDate, List<Db.Fast>>();
        for (Db.Fast f : fasts) {
            LocalDate d = Instant.ofEpochMilli(f.endAt).atZone(zone).toLocalDate();
            List<Db.Fast> l = byDay.get(d);
            if (l == null) byDay.put(d, l = new ArrayList<Db.Fast>());
            l.add(f);
        }

        LinearLayout card = Ui.card(c);
        card.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 10));

        final YearMonth now = YearMonth.now();
        LinearLayout nav = Ui.hbox(c);
        ImageView prev = Ui.iconButton(c, R.drawable.ic_left, R.color.text, "Previous month");
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { month = month.minusMonths(1); a.refresh(); }
        });
        ImageView next = Ui.iconButton(c, R.drawable.ic_right, R.color.text, "Next month");
        boolean canNext = month.isBefore(now);
        next.setEnabled(canNext);
        next.setAlpha(canNext ? 1f : 0.3f);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { month = month.plusMonths(1); a.refresh(); }
        });
        TextView title = Ui.text(c, month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())), 17, R.color.text, true);
        title.setGravity(Gravity.CENTER);
        nav.addView(prev);
        nav.addView(title, Ui.lp(0, Ui.WRAP, 1f));
        nav.addView(next);
        card.addView(nav);

        DayOfWeek first = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
        LinearLayout names = Ui.hbox(c);
        names.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 2));
        for (int i = 0; i < 7; i++) {
            TextView t = Ui.text(c, first.plus(i).getDisplayName(TextStyle.SHORT, Locale.getDefault()), 12, R.color.text2, true);
            t.setGravity(Gravity.CENTER);
            names.addView(t, Ui.lp(0, Ui.WRAP, 1f));
        }
        card.addView(names);

        LocalDate today = LocalDate.now();
        int lead = (month.atDay(1).getDayOfWeek().getValue() - first.getValue() + 7) % 7;
        int cells = (lead + month.lengthOfMonth() + 6) / 7 * 7;
        LinearLayout week = null;
        for (int i = 0; i < cells; i++) {
            // Week rows split the card's spare height, so the grid stretches or shrinks to fit the screen.
            if (i % 7 == 0) {
                week = Ui.hbox(c);
                card.addView(week, Ui.lp(Ui.MATCH, 0, 1f));
            }
            int day = i - lead + 1;
            if (day < 1 || day > month.lengthOfMonth()) {
                week.addView(new View(c), Ui.lp(0, Ui.MATCH, 1f));
            } else {
                LocalDate d = month.atDay(day);
                week.addView(dayCell(d, today, byDay.get(d)), Ui.lp(0, Ui.MATCH, 1f));
            }
        }

        LinearLayout legend = Ui.hbox(c);
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(0, Ui.dp(c, 8), 0, 0);
        legend.addView(Ui.icon(c, R.drawable.ic_day_met, R.color.success, 16));
        legend.addView(Ui.hspace(c, 6));
        legend.addView(Ui.muted(c, "Goal met"));
        legend.addView(Ui.hspace(c, 20));
        legend.addView(Ui.icon(c, R.drawable.ic_day_partial, R.color.warn, 16));
        legend.addView(Ui.hspace(c, 6));
        legend.addView(Ui.muted(c, "Ended early"));
        card.addView(legend);
        if (fasts.isEmpty()) {
            TextView t = Ui.muted(c, "Your completed fasts show here.");
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(c, 4), 0, 0);
            card.addView(t);
        }
        return card;
    }

    private View dayCell(final LocalDate d, LocalDate today, final List<Db.Fast> dayFasts) {
        LinearLayout cell = Ui.vbox(c);
        cell.setGravity(Gravity.CENTER);
        boolean isToday = d.equals(today);
        TextView num = Ui.big(c, String.valueOf(d.getDayOfMonth()), 13, isToday ? R.color.primary : R.color.text);
        num.setGravity(Gravity.CENTER);
        if (d.isAfter(today)) num.setAlpha(0.35f);
        if (isToday) num.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        cell.addView(num);
        if (dayFasts == null) {
            cell.addView(Ui.space(c, 22));
            return cell;
        }
        boolean met = false;
        for (Db.Fast f : dayFasts) met |= f.met();
        ImageView ic = Ui.icon(c, met ? R.drawable.ic_day_met : R.drawable.ic_day_partial, met ? R.color.success : R.color.warn, 20);
        ic.setLayoutParams(Ui.lpm(c, Ui.dp(c, 20), Ui.dp(c, 20), 0, 2, 0, 0));
        cell.addView(ic);
        cell.setContentDescription(Ui.dayLabel(d) + ", " + (met ? "goal met" : "ended early"));
        Ui.clickable(cell, 12);
        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { openDay(d, dayFasts); }
        });
        return cell;
    }

    /** One fast opens straight into the editor; several are listed on DayFastsScreen first. */
    private void openDay(LocalDate d, List<Db.Fast> dayFasts) {
        if (dayFasts.size() == 1) a.push(new PastFastScreen(a, dayFasts.get(0)));
        else a.push(new DayFastsScreen(a, d));
    }
}
