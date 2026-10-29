package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** First run, three steps: 1 disclaimer, 2 fasting plan, 3 about you. Continue is pinned at the bottom. */
final class OnboardingScreen extends Screen {
    static final int STEPS = 3;

    int step;
    private int direction;
    private boolean accepted;
    private String planId = "16:8";
    private int customHours = 16;
    private SettingsScreen.ProfileForm form;

    private static final String[] PLAN_NOTES = {
        "A gentle start: your overnight fast plus a little.",
        "The most popular plan. Skip breakfast or late snacks.",
        "Two meals inside a 6-hour window.",
        "One main meal and a snack.",
        "One meal a day. For experienced fasters.",
    };

    OnboardingScreen(MainActivity a) { super(a); }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));
        root.addView(topBar());

        LinearLayout col = Ui.column(c, 24);
        if (step == 0) disclaimer(col);
        else if (step == 1) plans(col);
        else about(col);
        ScrollView sv = Ui.scroll(c, col);
        root.addView(sv, Ui.lp(Ui.MATCH, 0, 1f));

        LinearLayout bottom = Ui.vbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        final Button next = Ui.primary(c, step == STEPS - 1 ? "Get started" : "Continue", R.color.primary);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { next(); }
        });
        if (step == 0) {
            setEnabled(next, accepted);
            gate = next;
        }
        bottom.addView(next, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));

        if (direction != 0) {
            col.setTranslationX(direction * Ui.dp(c, 56));
            col.setAlpha(0f);
            col.animate().translationX(0).alpha(1f).setDuration(280)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
            direction = 0;
        }
        return root;
    }

    private Button gate;

    private void setEnabled(Button b, boolean on) {
        b.setEnabled(on);
        b.setAlpha(on ? 1f : 0.4f);
    }

    private View topBar() {
        LinearLayout bar = Ui.hbox(c);
        bar.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 4));
        if (step > 0) {
            ImageView back = Ui.iconButton(c, R.drawable.ic_left, R.color.text, "Back");
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { back(); }
            });
            bar.addView(back);
        } else {
            bar.addView(Ui.hspace(c, 44));
        }
        LinearLayout segs = Ui.hbox(c);
        for (int i = 0; i < STEPS; i++) {
            View s = new View(c);
            s.setBackground(Ui.round(i <= step ? Ui.col(c, R.color.primary) : Ui.col(c, R.color.outline), Ui.dp(c, 3)));
            LinearLayout.LayoutParams lp = Ui.lp(0, Ui.dp(c, 6), 1f);
            if (i > 0) lp.setMarginStart(Ui.dp(c, 6));
            segs.addView(s, lp);
        }
        bar.addView(segs, Ui.lpm(c, 0, Ui.WRAP, 8, 0, 12, 0));
        ((LinearLayout.LayoutParams) segs.getLayoutParams()).weight = 1f;
        bar.addView(Ui.text(c, (step + 1) + " of " + STEPS, 13, R.color.text2, true));
        return bar;
    }

    private TextView title(String s) {
        TextView t = Ui.text(c, s, 28, R.color.text, true);
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 12), 0, Ui.dp(c, 4));
        return t;
    }

    private TextView subtitle(String s) {
        TextView t = Ui.text(c, s, 15, R.color.text2, false);
        t.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 16));
        return t;
    }

    // ---------- step 1 ----------

    private void disclaimer(LinearLayout col) {
        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.logo_mark);
        logo.setContentDescription("Fastwell logo");
        LinearLayout.LayoutParams lp = Ui.lpm(c, Ui.dp(c, 64), Ui.dp(c, 64), 4, 16, 0, 4);
        col.addView(logo, lp);
        col.addView(title("Welcome to Fastwell"));
        col.addView(subtitle("Track your fasts, meals and workouts in one place."));

        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, "Before you start", 17, R.color.text, true));
        TextView intro = Ui.body(c, "Fastwell gives general information, not medical advice. Talk to a doctor before fasting if you:");
        intro.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        card.addView(intro);
        String[] who = {
            "are pregnant or breastfeeding",
            "have diabetes or take medication",
            "are under 18",
            "have a history of eating disorders",
        };
        for (String w : who) {
            LinearLayout r = Ui.hbox(c);
            r.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), 0, Ui.dp(c, 4));
            View dot = new View(c);
            dot.setBackground(Ui.round(Ui.col(c, R.color.eat), Ui.dp(c, 3)));
            r.addView(dot, Ui.lp(Ui.dp(c, 6), Ui.dp(c, 6)));
            TextView t = Ui.body(c, w);
            t.setPadding(Ui.dp(c, 12), 0, 0, 0);
            r.addView(t);
            card.addView(r);
        }
        col.addView(card);

        LinearLayout ack = Ui.card(c);
        final CheckBox ok = new CheckBox(c);
        ok.setText("I've read this and want to continue");
        ok.setTextSize(15);
        ok.setTextColor(Ui.col(c, R.color.text));
        ok.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.col(c, R.color.primary)));
        ok.setChecked(accepted);
        ok.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                accepted = on;
                if (gate != null) setEnabled(gate, on);
            }
        });
        ack.addView(ok);
        col.addView(ack);
    }

    // ---------- step 2 ----------

    private void plans(LinearLayout col) {
        col.addView(title("Your fasting plan"));
        col.addView(subtitle("How long do you want to fast each day? You can change this any time."));
        // Cards restyle in place on tap; nothing is rebuilt, so the screen doesn't flicker or jump.
        final List<Choice> group = new ArrayList<Choice>();
        for (int i = 0; i < FastingLogic.PRESETS.length; i++) {
            final FastingLogic.Plan p = FastingLogic.PRESETS[i];
            String name = p.name.contains("  ") ? p.name.substring(p.name.indexOf("  ") + 2) : p.fastHours + " hour fast";
            final Choice ch = Choice.card(c, p.ratio(), name, PLAN_NOTES[i]);
            ch.onClick(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    planId = p.id;
                    Choice.select(group, ch);
                }
            });
            group.add(ch);
            col.addView(ch.view, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
            if (p.id.equals(planId)) ch.setSelected(true);
        }
        final Choice custom = Choice.card(c, customLead(), "Custom", customNote());
        custom.onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HourDial.show(a, customHours, new HourDial.IntCb() {
                    @Override
                    public void on(int h) {
                        customHours = h;
                        planId = FastingLogic.CUSTOM;
                        custom.setTexts(customLead(), customNote());
                        Choice.select(group, custom);
                    }
                });
            }
        });
        group.add(custom);
        col.addView(custom.view, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        if (FastingLogic.CUSTOM.equals(planId)) custom.setSelected(true);
        TextView hint = Ui.muted(c, "New to fasting? 13:11 or 16:8 are good places to start.");
        hint.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), 0, 0);
        col.addView(hint);
    }

    private boolean customChosen() { return FastingLogic.CUSTOM.equals(planId); }

    private String customLead() {
        return customChosen() ? customHours + ":" + (24 - customHours) : "\u2022\u2022:\u2022\u2022";
    }

    private String customNote() {
        return customChosen() ? customHours + "h fasting, " + (24 - customHours) + "h eating. Tap to adjust."
                : "Pick your own fasting hours.";
    }

    // ---------- step 3 ----------

    private void about(LinearLayout col) {
        col.addView(title("About you"));
        col.addView(subtitle("Used only to suggest a daily calorie target. Stored on this phone."));
        if (form == null) form = new SettingsScreen.ProfileForm(a, prefs);
        ViewGroup parent = (ViewGroup) form.view.getParent();
        if (parent != null) parent.removeView(form.view);
        LinearLayout card = Ui.card(c);
        card.addView(form.view);
        col.addView(card);
    }

    // ---------- navigation ----------

    private void next() {
        if (step == 0 && !accepted) return;
        if (step < STEPS - 1) {
            step++;
            direction = 1;
            a.rebuild();
            return;
        }
        prefs.setPlan(planId, customHours);
        if (form != null) form.save();
        prefs.setOnboarded(true);
        a.finishOnboarding();
    }

    void back() {
        if (step == 0) return;
        if (step == 2 && form != null) form.saveProfile();
        step--;
        direction = -1;
        a.rebuild();
    }
}
