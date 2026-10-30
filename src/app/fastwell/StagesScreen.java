package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Every fasting stage as a read-only card, opened from the Fast screen's "Current stage" card. */
final class StagesScreen extends Screen {
    private static final int[] ICONS = {
        R.drawable.ic_food,
        R.drawable.ic_stage_drop,
        R.drawable.ic_stage_flame,
        R.drawable.ic_stage_bolt,
        R.drawable.ic_stage_renew,
        R.drawable.ic_stage_heart,
    };

    /** General information, matching FastingLogic.STAGE_NAMES; not medical claims. */
    private static final String[] NOTES = {
        "Your body is digesting and absorbing your last meal. Blood sugar and insulin rise, and energy comes mostly from the food you just ate.",
        "Insulin drops back toward baseline and blood sugar levels out. Your body starts drawing on glycogen, the sugar stored in your liver.",
        "With liver glycogen running low, your body turns more to stored fat for fuel and starts making small amounts of ketones.",
        "Ketone levels keep climbing as fat becomes a main fuel. Many people notice steadier energy and less hunger around here.",
        "Ketones are now a major fuel for your body and brain. Autophagy, the body's cell clean-up process, is thought to pick up around this point.",
        "Growth hormone rises and your body works to preserve muscle. Fasts this long should be medically supervised.",
    };

    private Db.Fast active;
    /** Stage of the running fast, or -1 when not fasting. */
    private int current = -1;

    StagesScreen(MainActivity a) { super(a); }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        LinearLayout header = FastHistoryScreen.backHeader(a, "Fasting stages");
        header.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0);
        root.addView(header);

        active = db.activeFast();
        current = stageNow();
        LinearLayout col = Ui.column(c, 24);
        col.addView(OnboardingScreen.subtitle(c, "What generally happens in your body as a fast goes on. Everyone is different, so timings are approximate."));
        for (int i = 0; i < FastingLogic.STAGE_NAMES.length; i++) {
            col.addView(stageCard(i), Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        }
        TextView note = Ui.text(c, "General information, not medical advice.", 11, R.color.text2, false);
        note.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), 0, 0);
        col.addView(note);
        root.addView(Ui.scroll(c, col), Ui.lp(Ui.MATCH, 0, 1f));
        return root;
    }

    /** Laid out like an unselected plan card, without the tap target or selection tick. */
    private View stageCard(int i) {
        boolean now = i == current;
        LinearLayout card = Ui.hbox(c);
        card.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        card.setBackground(Ui.round(Ui.col(c, R.color.surface), Ui.dp(c, 16), Ui.col(c, R.color.outline), Ui.dp(c, 1)));

        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Ui.round(Ui.alpha(Ui.col(c, R.color.primary), Ui.night(c) ? 0.22f : 0.14f), Ui.dp(c, 12)));
        badge.addView(Ui.icon(c, ICONS[i], R.color.primary, 22),
                new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER));
        card.addView(badge, Ui.lp(Ui.dp(c, 44), Ui.dp(c, 44)));
        card.addView(Ui.hspace(c, 14));

        LinearLayout mid = Ui.vbox(c);
        LinearLayout head = Ui.hbox(c);
        head.addView(Ui.text(c, hours(i), 12, R.color.primary, true), Ui.lp(0, Ui.WRAP, 1f));
        if (now) head.addView(Ui.chip(c, "Now", R.color.primary));
        mid.addView(head);
        mid.addView(Ui.text(c, FastingLogic.STAGE_NAMES[i], 16, R.color.text, true));
        TextView desc = Ui.muted(c, NOTES[i]);
        desc.setPadding(0, Ui.dp(c, 2), 0, 0);
        mid.addView(desc);
        card.addView(mid, Ui.lp(0, Ui.WRAP, 1f));

        card.setContentDescription(hours(i) + ", " + FastingLogic.STAGE_NAMES[i] + (now ? ", current stage" : "") + ". " + NOTES[i]);
        return card;
    }

    /** "0-4 hours", "48+ hours". */
    private static String hours(int i) {
        int[] h = FastingLogic.STAGE_HOURS;
        return i + 1 < h.length ? h[i] + "–" + h[i + 1] + " hours" : h[i] + "+ hours";
    }

    private int stageNow() {
        if (active == null) return -1;
        return FastingLogic.stageIndex(active.durationMs(System.currentTimeMillis()) / 3_600_000.0);
    }

    @Override
    void tick() {
        // Moves the "Now" chip when the running fast crosses into the next stage.
        if (stageNow() != current) a.refresh();
    }
}
