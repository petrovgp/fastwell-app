package app.fastwell;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Selectable card used for fasting plans, activity levels and the sex toggle.
 * Selection is restyled in place, so picking an option never rebuilds (or flickers) the screen.
 */
final class Choice {
    final LinearLayout view;
    private final Context c;
    private final TextView lead, title, note;
    private final FrameLayout tick;
    private final boolean compact;
    private boolean selected;

    private Choice(Context c, String leadText, View leadView, String titleText, String noteText, boolean compact) {
        this.c = c;
        this.compact = compact;
        view = Ui.hbox(c);
        if (compact) {
            view.setGravity(Gravity.CENTER);
            view.setPadding(Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14));
            view.setMinimumHeight(Ui.dp(c, 52));
        } else {
            view.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        }

        if (leadText != null) {
            lead = Ui.big(c, leadText, 22, R.color.text);
            lead.setGravity(Gravity.CENTER_VERTICAL);
            lead.setSingleLine(true);
            view.addView(lead, Ui.lp(Ui.dp(c, 76), Ui.WRAP));
        } else {
            lead = null;
            if (leadView != null) {
                LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(c, 60), Ui.WRAP);
                view.addView(leadView, lp);
            }
        }

        LinearLayout mid = Ui.vbox(c);
        if (compact) mid.setGravity(Gravity.CENTER_HORIZONTAL);
        title = Ui.text(c, titleText, 16, R.color.text, true);
        mid.addView(title);
        note = Ui.muted(c, noteText == null ? "" : noteText);
        if (noteText == null) note.setVisibility(View.GONE);
        mid.addView(note);
        view.addView(mid, compact ? Ui.lp(Ui.WRAP, Ui.WRAP) : Ui.lp(0, Ui.WRAP, 1f));

        if (!compact) {
            tick = new FrameLayout(c);
            LinearLayout.LayoutParams tp = Ui.lp(Ui.dp(c, 24), Ui.dp(c, 24));
            tp.setMarginStart(Ui.dp(c, 12));
            view.addView(tick, tp);
        } else {
            tick = null;
        }
        Ui.clickable(view, 16);
        apply();
    }

    static Choice card(Context c, String leadText, String title, String note) {
        return new Choice(c, leadText, null, title, note, false);
    }

    static Choice card(Context c, View leadView, String title, String note) {
        return new Choice(c, null, leadView, title, note, false);
    }

    /** Centered, title-only option for side-by-side toggles. */
    static Choice toggle(Context c, String title) {
        return new Choice(c, null, null, title, null, true);
    }

    Choice onClick(View.OnClickListener l) {
        view.setOnClickListener(l);
        return this;
    }

    void setTexts(String leadText, String noteText) {
        if (lead != null && leadText != null) lead.setText(leadText);
        if (noteText != null) note.setText(noteText);
    }

    boolean isSelected() { return selected; }

    void setSelected(boolean s) {
        selected = s;
        apply();
    }

    /** Selects one choice in a group and clears the rest. */
    static void select(List<Choice> group, Choice which) {
        for (Choice ch : group) ch.setSelected(ch == which);
    }

    private void apply() {
        int primary = Ui.col(c, R.color.primary);
        int fill = selected ? Ui.alpha(primary, Ui.night(c) ? 0.18f : 0.07f) : Ui.col(c, R.color.surface);
        int stroke = selected ? primary : Ui.col(c, R.color.outline);
        view.setBackground(Ui.ripple(c, Ui.round(fill, Ui.dp(c, 16), stroke, Ui.dp(c, selected ? 2 : 1)), Ui.dp(c, 16)));
        if (lead != null) lead.setTextColor(Ui.col(c, selected ? R.color.primary : R.color.text));
        if (compact) title.setTextColor(Ui.col(c, selected ? R.color.primary : R.color.text));
        if (tick != null) {
            tick.removeAllViews();
            tick.setBackground(selected ? Ui.round(primary, Ui.dp(c, 12))
                    : Ui.round(0, Ui.dp(c, 12), Ui.col(c, R.color.outline), Ui.dp(c, 2)));
            if (selected) {
                View dot = new View(c);
                dot.setBackground(Ui.round(Ui.col(c, R.color.on_primary), Ui.dp(c, 4)));
                tick.addView(dot, new FrameLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8), Gravity.CENTER));
            }
        }
        view.setContentDescription(title.getText() + (note.getVisibility() == View.VISIBLE ? ", " + note.getText() : "")
                + (selected ? ", selected" : ""));
        view.setSelected(selected);
    }

    /** Five rising bars with the first {@code level + 1} filled; lead graphic for activity levels. */
    static View levelBars(Context c, int level) {
        LinearLayout row = Ui.hbox(c);
        row.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < 5; i++) {
            View b = new View(c);
            int color = i <= level ? Ui.col(c, R.color.primary) : Ui.col(c, R.color.outline);
            b.setBackground(Ui.round(color, Ui.dp(c, 2)));
            LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(c, 6), Ui.dp(c, 8 + i * 4));
            lp.setMarginEnd(Ui.dp(c, 3));
            row.addView(b, lp);
        }
        return row;
    }
}
