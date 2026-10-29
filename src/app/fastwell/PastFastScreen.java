package app.fastwell;

import android.app.AlertDialog;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Full-screen editor for adding or editing a completed fast: tap Start or End, then set it with the
 * same day and time dials as EditStartScreen. The back arrow cancels.
 */
final class PastFastScreen extends Screen {
    /** How far back the day dial reaches. */
    static final int DAYS_BACK = 60;

    private final Db.Fast fast = new Db.Fast();
    /** The fast being edited, or null when adding a new one. */
    private final Db.Fast original;
    private final LocalDate firstDay;
    private final int dayCount;
    private boolean editingEnd;

    private Dial dayDial, timeDial;
    private int hourCol, minCol, ampmCol = -1;
    private LinearLayout startBox, endBox;
    private TextView startVal, endVal, status;
    private Button save;

    /** Adds a new past fast. */
    PastFastScreen(MainActivity a) { this(a, null); }

    /** Edits {@code existing} (a copy is edited, so leaving with the back arrow changes nothing), or adds a new fast if null. */
    PastFastScreen(MainActivity a, Db.Fast existing) {
        super(a);
        original = existing;
        if (existing != null) {
            fast.id = existing.id;
            fast.startAt = existing.startAt;
            fast.endAt = existing.endAt;
            fast.goalMin = existing.goalMin;
            fast.plan = existing.plan;
            fast.mood = existing.mood;
            fast.note = existing.note;
        } else {
            fast.goalMin = prefs.plan().goalMinutes();
            fast.plan = prefs.plan().id;
            fast.endAt = (System.currentTimeMillis() - 60000) / 60000 * 60000;
            fast.startAt = fast.endAt - fast.goalMs();
        }
        LocalDate today = LocalDate.now();
        LocalDate first = today.minusDays(DAYS_BACK);
        if (day(fast.startAt).isBefore(first)) first = day(fast.startAt);
        firstDay = first;
        dayCount = (int) ChronoUnit.DAYS.between(firstDay, today) + 1;
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        LinearLayout col = Ui.vbox(c);
        col.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), 0);
        col.addView(FastHistoryScreen.backHeader(a, original == null ? "Add a past fast" : "Edit fast"));
        col.addView(summaryCard());

        LocalDateTime t = time(selected());
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
        save = Ui.primary(c, original == null ? "Save fast" : "Save", R.color.primary);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (problem() != null) return;
                if (original == null) {
                    db.insertFast(fast);
                    a.pop();
                    a.snack("Fast added", new Runnable() {
                        @Override
                        public void run() { db.deleteFast(fast.id); }
                    });
                } else {
                    db.updateFast(fast);
                    a.pop();
                    a.snack("Fast updated", new Runnable() {
                        @Override
                        public void run() { db.updateFast(original); }
                    });
                }
            }
        });
        if (original == null) {
            bottom.addView(save, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        } else {
            LinearLayout buttons = Ui.hbox(c);
            Button delete = Ui.tonal(c, "Delete", R.color.error);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { confirmDelete(); }
            });
            buttons.addView(delete, Ui.lp(0, Ui.dp(c, 56), 1f));
            buttons.addView(Ui.hspace(c, 12));
            buttons.addView(save, Ui.lp(0, Ui.dp(c, 56), 1f));
            bottom.addView(buttons, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));
        update();
        return root;
    }

    /** Asks before deleting, with a split Cancel / Delete button row. */
    private void confirmDelete() {
        LinearLayout body = Ui.dialogBody(c);
        TextView msg = Ui.body(c, "The " + FastingLogic.dur(original.endAt - original.startAt) + " fast from "
                + Ui.dateTime(c, original.startAt) + " will be removed from your history.");
        msg.setTextColor(Ui.col(c, R.color.text2));
        body.addView(msg);
        LinearLayout buttons = Ui.hbox(c);
        buttons.setPadding(0, Ui.dp(c, 20), 0, Ui.dp(c, 20));
        Button delete = Ui.tonal(c, "Delete", R.color.error);
        Button cancel = Ui.primary(c, "Cancel", R.color.primary);
        buttons.addView(cancel, Ui.lp(0, Ui.dp(c, 56), 1f));
        buttons.addView(Ui.hspace(c, 12));
        buttons.addView(delete, Ui.lp(0, Ui.dp(c, 56), 1f));
        body.addView(buttons, Ui.lp(Ui.MATCH, Ui.WRAP));

        final AlertDialog dlg = new AlertDialog.Builder(c)
                .setTitle("Delete this fast?")
                .setView(body)
                .create();
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                db.deleteFast(original.id);
                a.pop();
                a.snack("Fast deleted", new Runnable() {
                    @Override
                    public void run() { db.insertFast(original); }
                });
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dlg.dismiss(); }
        });
        dlg.show();
    }

    /** Start and End side by side; the one being edited is highlighted, tap the other to switch. */
    private View summaryCard() {
        LinearLayout card = Ui.card(c);
        card.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 12));
        LinearLayout row = Ui.hbox(c);
        startBox = Ui.vbox(c);
        startBox.addView(Ui.muted(c, "Start"));
        startVal = Ui.text(c, "", 16, R.color.text, true);
        startBox.addView(startVal);
        row.addView(startBox, Ui.lp(0, Ui.WRAP, 1f));
        row.addView(Ui.hspace(c, 8));
        endBox = Ui.vbox(c);
        endBox.addView(Ui.muted(c, "End"));
        endVal = Ui.text(c, "", 16, R.color.text, true);
        endBox.addView(endVal);
        row.addView(endBox, Ui.lp(0, Ui.WRAP, 1f));
        startBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { select(false); }
        });
        endBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { select(true); }
        });
        card.addView(row);
        status = Ui.text(c, "", 13, R.color.primary, true);
        status.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), 0);
        card.addView(status);
        return card;
    }

    private void select(boolean end) {
        editingEnd = end;
        LocalDateTime t = time(selected());
        dayDial.setValue(0, ChronoUnit.DAYS.between(firstDay, t.toLocalDate()));
        if (ampmCol >= 0) {
            int h12 = t.getHour() % 12;
            timeDial.setValue(hourCol, h12 == 0 ? 12 : h12);
            timeDial.setValue(ampmCol, t.getHour() >= 12 ? 1 : 0);
        } else {
            timeDial.setValue(hourCol, t.getHour());
        }
        timeDial.setValue(minCol, t.getMinute());
        update();
    }

    private long selected() { return editingEnd ? fast.endAt : fast.startAt; }

    private void readDials() {
        LocalDate d = firstDay.plusDays((long) dayDial.value(0));
        int h = (int) timeDial.value(hourCol);
        if (ampmCol >= 0) h = h % 12 + (timeDial.value(ampmCol) >= 1 ? 12 : 0);
        int m = (int) timeDial.value(minCol);
        long t = LocalDateTime.of(d.getYear(), d.getMonthValue(), d.getDayOfMonth(), h, m)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (editingEnd) fast.endAt = t; else fast.startAt = t;
        update();
    }

    /** Why the fast can't be saved, or null if it can. */
    private String problem() {
        if (fast.endAt <= fast.startAt) return "End must be after the start";
        if (fast.endAt > System.currentTimeMillis()) return "End can't be in the future";
        Db.Fast active = db.activeFast();
        if (active != null && fast.endAt > active.startAt) return "That overlaps your current fast";
        for (Db.Fast f : db.completedFastsSince(fast.startAt)) {
            if (f.id != fast.id && f.startAt < fast.endAt) return "That overlaps a fast from " + Ui.dateTime(c, f.startAt);
        }
        return null;
    }

    private void update() {
        if (startVal == null) return;
        startVal.setText(Ui.dateTime(c, fast.startAt));
        endVal.setText(Ui.dateTime(c, fast.endAt));
        int on = Ui.alpha(Ui.col(c, R.color.primary), Ui.night(c) ? 0.22f : 0.12f);
        startBox.setBackground(Ui.round(editingEnd ? 0 : on, Ui.dp(c, 12)));
        endBox.setBackground(Ui.round(editingEnd ? on : 0, Ui.dp(c, 12)));
        Ui.clickable(startBox, 12);
        Ui.clickable(endBox, 12);
        int pad = Ui.dp(c, 8);
        startBox.setPadding(pad, pad, pad, pad);
        endBox.setPadding(pad, pad, pad, pad);
        // One status line: the reason it can't be saved, otherwise the length against the goal.
        String p = problem();
        boolean met = p == null && fast.met();
        status.setText(p != null ? p : FastingLogic.dur(fast.endAt - fast.startAt) + " of your "
                + FastingLogic.dur(fast.goalMs()) + " goal" + (met ? "  -  goal met" : "  -  ended early"));
        status.setTextColor(Ui.col(c, p != null ? R.color.error : met ? R.color.success : R.color.warn));
        save.setEnabled(p == null);
        save.setAlpha(p == null ? 1f : 0.4f);
    }

    private static LocalDateTime time(long t) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault());
    }

    private static LocalDate day(long t) {
        return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
