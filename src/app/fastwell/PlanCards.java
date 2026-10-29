package app.fastwell;

import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/** Fasting plan cards (presets plus custom), shared by onboarding and the Fast screen's plan picker. */
final class PlanCards {
    private PlanCards() {}

    interface Cb { void on(String planId, int customHours); }

    private static final String[] NOTES = {
        "A gentle start: your overnight fast plus a little.",
        "The most popular plan. Skip breakfast or late snacks.",
        "Two meals inside a 6-hour window.",
        "One main meal and a snack.",
        "One meal a day. For experienced fasters.",
    };

    /** Adds one card per plan to {@code col}; {@code onPick} runs when a preset is tapped or a custom length is set. */
    static void add(final MainActivity a, LinearLayout col, String planId, int customHours, final Cb onPick) {
        // Cards restyle in place on tap; nothing is rebuilt, so the screen doesn't flicker or jump.
        final List<Choice> group = new ArrayList<Choice>();
        final int[] hours = {Math.min(HourDial.MAX, customHours)};
        for (int i = 0; i < FastingLogic.PRESETS.length; i++) {
            final FastingLogic.Plan p = FastingLogic.PRESETS[i];
            String name = p.name.contains("  ") ? p.name.substring(p.name.indexOf("  ") + 2) : p.fastHours + " hour fast";
            final Choice ch = Choice.card(a, p.ratio(), name, NOTES[i]);
            ch.onClick(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Choice.select(group, ch);
                    onPick.on(p.id, hours[0]);
                }
            });
            group.add(ch);
            col.addView(ch.view, Ui.lpm(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
            if (p.id.equals(planId)) ch.setSelected(true);
        }
        final boolean chosen = FastingLogic.CUSTOM.equals(planId);
        final Choice custom = Choice.card(a, customLead(chosen, hours[0]), "Custom", customNote(chosen, hours[0]));
        custom.onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HourDial.show(a, hours[0], new HourDial.IntCb() {
                    @Override
                    public void on(int h) {
                        hours[0] = h;
                        custom.setTexts(customLead(true, h), customNote(true, h));
                        Choice.select(group, custom);
                        onPick.on(FastingLogic.CUSTOM, h);
                    }
                });
            }
        });
        group.add(custom);
        col.addView(custom.view, Ui.lpm(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        if (chosen) custom.setSelected(true);
    }

    private static String customLead(boolean chosen, int h) {
        return chosen ? h + ":" + (24 - h) : "••:••";
    }

    private static String customNote(boolean chosen, int h) {
        return chosen ? h + "h fasting, " + (24 - h) + "h eating. Tap to adjust." : "Pick your own fasting hours.";
    }
}
