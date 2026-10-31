package app.fastwell;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen end of a running fast: total time, the end it will be saved with, and an optional mood. The back arrow cancels.
 * A fast under SHORT_MS asks first whether to save it or discard it.
 */
final class EndFastScreen extends Screen {
    /** Top to bottom as shown; the stored mood is 5 for Great down to 1 for Rough (0 = not picked). */
    static final String[] MOODS = {"Great", "Good", "Neutral", "Bad", "Rough"};
    /** The face shown on each mood's card, in MOODS order. */
    private static final int[] MOOD_ICONS = {R.drawable.ic_mood_great, R.drawable.ic_mood_good, R.drawable.ic_mood_neutral,
            R.drawable.ic_mood_bad, R.drawable.ic_mood_rough};
    /** Fasts shorter than this ask whether to save or discard them. */
    static final long SHORT_MS = 60 * 60000L;

    private final Db.Fast fast;
    private int mood;
    private TextView total, goal, endVal;

    EndFastScreen(MainActivity a, Db.Fast f) {
        super(a);
        fast = f;
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        LinearLayout bar = FastHistoryScreen.backHeader(a, "End fast");
        bar.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 4));
        root.addView(bar);

        LinearLayout col = Ui.column(c, 24);
        col.addView(summaryCard(), Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 0, 0, 20));

        TextView feel = Ui.text(c, "How did it feel?", 18, R.color.text, true);
        feel.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 12));
        col.addView(feel);
        // Tapping the picked mood again clears it, since the mood is optional.
        final List<Choice> group = new ArrayList<Choice>();
        for (int i = 0; i < MOODS.length; i++) {
            final int value = MOODS.length - i;
            final Choice ch = Choice.card(c, moodIcon(i), MOODS[i], null);
            ch.onClick(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mood = mood == value ? 0 : value;
                    Choice.select(group, mood == 0 ? null : ch);
                }
            });
            group.add(ch);
            col.addView(ch.view, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        }
        root.addView(Ui.scroll(c, col), Ui.lp(Ui.MATCH, 0, 1f));

        LinearLayout bottom = Ui.vbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        Button save = Ui.primary(c, "Save", R.color.primary);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { save(); }
        });
        bottom.addView(save, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));
        update();
        return root;
    }

    private View moodIcon(int i) {
        LinearLayout wrap = Ui.hbox(c);
        wrap.addView(Ui.icon(c, MOOD_ICONS[i], R.color.primary, 28));
        return wrap;
    }

    private View summaryCard() {
        LinearLayout card = Ui.card(c);
        card.addView(Ui.muted(c, "Total time"));
        total = Ui.big(c, "", 40, R.color.text);
        card.addView(total);
        goal = Ui.text(c, "", 13, R.color.primary, true);
        card.addView(goal);
        TextView ends = Ui.muted(c, "Finished");
        ends.setPadding(0, Ui.dp(c, 12), 0, 0);
        card.addView(ends);
        endVal = Ui.text(c, "", 16, R.color.text, true);
        card.addView(endVal);
        return card;
    }

    private void update() {
        if (total == null) return;
        long end = System.currentTimeMillis();
        long d = end - fast.startAt;
        boolean met = d >= fast.goalMs();
        total.setText(FastingLogic.dur(d));
        goal.setText(met ? "Goal of " + FastingLogic.dur(fast.goalMs()) + " met"
                : FastingLogic.dur(fast.goalMs() - d) + " short of your " + FastingLogic.dur(fast.goalMs()) + " goal");
        goal.setTextColor(Ui.col(c, met ? R.color.success : R.color.warn));
        endVal.setText(Ui.dateTime(c, end));
    }

    @Override
    void tick() { update(); }

    private void save() {
        long d = System.currentTimeMillis() - fast.startAt;
        if (d < SHORT_MS) confirmShort(d); else saveNow();
    }

    /** Offers to drop a fast shorter than SHORT_MS, with the same split Discard / Save row as PastFastScreen's delete dialog. */
    private void confirmShort(long d) {
        LinearLayout body = Ui.dialogBody(c);
        TextView msg = Ui.body(c, "You fasted " + FastingLogic.dur(d) + ". Very short fasts lower your average and goal-met rate,"
                + " so you may want to leave this one out of your history.");
        msg.setTextColor(Ui.col(c, R.color.text2));
        body.addView(msg);
        LinearLayout buttons = Ui.hbox(c);
        buttons.setPadding(0, Ui.dp(c, 20), 0, Ui.dp(c, 20));
        Button discard = Ui.tonal(c, "Discard", R.color.error);
        Button keep = Ui.primary(c, "Save", R.color.primary);
        buttons.addView(discard, Ui.lp(0, Ui.dp(c, 56), 1f));
        buttons.addView(Ui.hspace(c, 12));
        buttons.addView(keep, Ui.lp(0, Ui.dp(c, 56), 1f));
        body.addView(buttons, Ui.lp(Ui.MATCH, Ui.WRAP));

        final AlertDialog dlg = new AlertDialog.Builder(c)
                .setTitle("Save this short fast?")
                .setView(body)
                .create();
        discard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                discard();
            }
        });
        keep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                saveNow();
            }
        });
        dlg.show();
    }

    private void discard() {
        db.deleteFast(fast.id);
        Notifier.refresh(a);
        a.pop();
        a.snack("Fast discarded", new Runnable() {
            @Override
            public void run() {
                if (db.activeFast() == null) db.insertFast(fast);
            }
        });
    }

    private void saveNow() {
        final long end = System.currentTimeMillis();
        db.endFast(fast.id, end, mood, "");
        Notifier.refresh(a);
        a.pop();
        a.snack("Fast saved  -  " + FastingLogic.dur(end - fast.startAt), new Runnable() {
            @Override
            public void run() {
                Db.Fast back = db.getFast(fast.id);
                if (back != null && db.activeFast() == null) {
                    back.endAt = 0;
                    back.mood = 0;
                    back.note = "";
                    db.updateFast(back);
                }
            }
        });
    }
}
