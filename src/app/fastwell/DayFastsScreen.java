package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The fasts that ended on one calendar day, oldest first; tap one to edit or delete it in PastFastScreen. */
final class DayFastsScreen extends Screen {
    private final LocalDate day;

    DayFastsScreen(MainActivity a, LocalDate day) {
        super(a);
        this.day = day;
    }

    @Override
    int navTab() { return MainActivity.TAB_FAST; }

    @Override
    View build() {
        LinearLayout col = Ui.column(c, 24);
        col.addView(FastHistoryScreen.backHeader(a, Ui.dayLabel(day)));

        // Re-queried on every build, so edits and deletes made in PastFastScreen show on return.
        List<Db.Fast> fasts = fastsEnding(db, day);
        if (fasts.isEmpty()) {
            LinearLayout empty = Ui.card(c);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView t = Ui.body(c, "No fasts ended on this day.");
            t.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
            empty.addView(t);
            col.addView(empty);
            return Ui.scroll(c, col);
        }

        long total = 0;
        int met = 0;
        for (Db.Fast f : fasts) {
            total += f.endAt - f.startAt;
            if (f.met()) met++;
        }
        TextView sum = Ui.muted(c, fasts.size() + (fasts.size() == 1 ? " fast" : " fasts") + "  -  "
                + FastingLogic.dur(total) + " fasted  -  " + met + " goal" + (met == 1 ? "" : "s") + " met");
        sum.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 8));
        col.addView(sum);

        for (final Db.Fast f : fasts) {
            LinearLayout card = Ui.card(c);
            card.addView(row(f));
            Ui.clickable(card, 16);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { a.push(new PastFastScreen(a, f)); }
            });
            col.addView(card);
        }
        return Ui.scroll(c, col);
    }

    /** Completed fasts whose end falls on {@code d} in the system zone, oldest first. */
    static List<Db.Fast> fastsEnding(Db db, LocalDate d) {
        ZoneId zone = ZoneId.systemDefault();
        long from = d.atStartOfDay(zone).toInstant().toEpochMilli();
        long to = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        List<Db.Fast> out = new ArrayList<Db.Fast>();
        for (Db.Fast f : db.completedFastsSince(from)) {
            if (f.endAt < to) out.add(f);
        }
        Collections.reverse(out);
        return out;
    }

    private View row(Db.Fast f) {
        boolean met = f.met();
        int color = met ? R.color.success : R.color.warn;
        LinearLayout r = Ui.hbox(c);
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Ui.round(Ui.alpha(Ui.col(c, color), Ui.night(c) ? 0.22f : 0.14f), Ui.dp(c, 12)));
        badge.addView(Ui.icon(c, met ? R.drawable.ic_day_met : R.drawable.ic_day_partial, color, 22),
                new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER));
        r.addView(badge, Ui.lp(Ui.dp(c, 44), Ui.dp(c, 44)));
        r.addView(Ui.hspace(c, 12));

        LinearLayout mid = Ui.vbox(c);
        mid.addView(Ui.text(c, FastingLogic.dur(f.endAt - f.startAt), 16, R.color.text, true));
        // Fasts often start the evening before, so the start gets its date when it isn't on this day.
        boolean sameDay = Instant.ofEpochMilli(f.startAt).atZone(ZoneId.systemDefault()).toLocalDate().equals(day);
        mid.addView(Ui.muted(c, (sameDay ? Ui.time(c, f.startAt) : Ui.dateTime(c, f.startAt)) + "  -  " + Ui.time(c, f.endAt)
                + "  -  goal " + FastingLogic.dur(f.goalMs())));
        r.addView(mid, Ui.lp(0, Ui.WRAP, 1f));
        r.addView(Ui.chip(c, met ? "Goal met" : "Ended early", color));

        r.setContentDescription(FastingLogic.dur(f.endAt - f.startAt) + " fast, " + (met ? "goal met" : "ended early"));
        return r;
    }
}
