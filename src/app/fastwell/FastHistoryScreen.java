package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Completed fasts with streaks and stats; tap a fast to edit or delete it, or add a past one. */
final class FastHistoryScreen extends Screen {

    FastHistoryScreen(MainActivity a) { super(a); }

    @Override
    int navTab() { return MainActivity.TAB_FAST; }

    @Override
    View build() {
        LinearLayout col = Ui.column(c, 24);
        col.addView(backHeader(a, "Fasting history"));

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
        LinearLayout r1 = Ui.hbox(c);
        r1.addView(stat("Current streak", st[0] + (st[0] == 1 ? " day" : " days")), Ui.lp(0, Ui.WRAP, 1f));
        r1.addView(stat("Best streak", st[1] + (st[1] == 1 ? " day" : " days")), Ui.lp(0, Ui.WRAP, 1f));
        stats.addView(r1);
        stats.addView(Ui.divider(c));
        LinearLayout r2 = Ui.hbox(c);
        r2.addView(stat("Fasts", String.valueOf(fasts.size())), Ui.lp(0, Ui.WRAP, 1f));
        r2.addView(stat("Goal met", fasts.isEmpty() ? "-" : Math.round(100.0 * met / fasts.size()) + "%"), Ui.lp(0, Ui.WRAP, 1f));
        r2.addView(stat("Average", fasts.isEmpty() ? "-" : FastingLogic.dur(total / fasts.size())), Ui.lp(0, Ui.WRAP, 1f));
        stats.addView(r2);
        col.addView(stats);

        Button add = Ui.tonal(c, "Add a past fast", R.color.primary);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Db.Fast f = new Db.Fast();
                long now = System.currentTimeMillis();
                f.goalMin = prefs.plan().goalMinutes();
                f.plan = prefs.plan().id;
                f.endAt = now - 60000;
                f.startAt = f.endAt - f.goalMs();
                editDialog(f, true);
            }
        });
        col.addView(add, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 48), 0, 0, 0, 8));

        if (fasts.isEmpty()) {
            TextView t = Ui.muted(c, "Your completed fasts show here.");
            t.setPadding(Ui.dp(c, 4), Ui.dp(c, 16), 0, 0);
            col.addView(t);
        } else {
            col.addView(Ui.sectionTitle(c, "All fasts"));
            LinearLayout list = Ui.card(c);
            list.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
            for (int i = 0; i < fasts.size(); i++) {
                if (i > 0) list.addView(Ui.divider(c));
                list.addView(row(fasts.get(i)));
            }
            col.addView(list);
        }
        return Ui.scroll(c, col);
    }

    static View backHeader(final MainActivity a, String title) {
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
        v.addView(Ui.big(c, value, 20, R.color.text));
        return v;
    }

    private View row(final Db.Fast f) {
        LinearLayout r = Ui.hbox(c);
        r.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        LinearLayout left = Ui.vbox(c);
        left.addView(Ui.text(c, Ui.dateTime(c, f.startAt), 15, R.color.text, true));
        String sub = "to " + Ui.dateTime(c, f.endAt) + "  -  goal " + FastingLogic.dur(f.goalMs());
        if (f.mood > 0) sub += "  -  " + FastScreen.MOODS[f.mood - 1];
        left.addView(Ui.muted(c, sub));
        if (!f.note.isEmpty()) left.addView(Ui.text(c, f.note, 13, R.color.text, false));
        r.addView(left, Ui.lp(0, Ui.WRAP, 1f));
        LinearLayout right = Ui.vbox(c);
        right.setGravity(android.view.Gravity.END);
        right.addView(Ui.big(c, FastingLogic.dur(f.endAt - f.startAt), 16, R.color.text));
        right.addView(Ui.chip(c, f.met() ? "Goal met" : "Ended early", f.met() ? R.color.success : R.color.text2));
        r.addView(right);
        Ui.clickable(r, 8);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { editDialog(f, false); }
        });
        return r;
    }

    private void editDialog(final Db.Fast f, final boolean isNew) {
        LinearLayout body = Ui.dialogBody(c);
        final Button start = Ui.tonal(c, "", R.color.primary);
        final Button end = Ui.tonal(c, "", R.color.primary);
        final TextView length = Ui.muted(c, "");
        final Runnable update = new Runnable() {
            @Override
            public void run() {
                start.setText("Start: " + Ui.dateTime(c, f.startAt));
                end.setText("End: " + Ui.dateTime(c, f.endAt));
                length.setText("Length " + FastingLogic.dur(f.endAt - f.startAt) + "  -  goal " + FastingLogic.dur(f.goalMs()));
            }
        };
        update.run();
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.pickDateTime(f.startAt, new MainActivity.LongCb() {
                    @Override
                    public void on(long t) { f.startAt = t; update.run(); }
                });
            }
        });
        end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.pickDateTime(f.endAt, new MainActivity.LongCb() {
                    @Override
                    public void on(long t) { f.endAt = t; update.run(); }
                });
            }
        });
        body.addView(start, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 44), 0, 8, 0, 8));
        body.addView(end, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 44), 0, 0, 0, 8));
        body.addView(length);

        AlertDialog.Builder b = new AlertDialog.Builder(c)
                .setTitle(isNew ? "Add past fast" : "Edit fast")
                .setView(body)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (f.endAt <= f.startAt) { Ui.toast(c, "End must be after the start"); return; }
                        if (f.endAt > System.currentTimeMillis() + 60000) { Ui.toast(c, "End can't be in the future"); return; }
                        if (isNew) db.insertFast(f); else db.updateFast(f);
                        a.refresh();
                    }
                })
                .setNegativeButton("Cancel", null);
        if (!isNew) {
            b.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    db.deleteFast(f.id);
                    a.refresh();
                    a.snack("Fast deleted", new Runnable() {
                        @Override
                        public void run() { db.insertFast(f); }
                    });
                }
            });
        }
        b.show();
    }
}
