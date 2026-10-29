package app.fastwell;

import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Short white loading screen: logo and name centered. MainActivity drops the next screen in from the top. */
final class LaunchScreen extends Screen {
    static final long DURATION_MS = 1200;

    LaunchScreen(MainActivity a) { super(a); }

    @Override
    boolean showsNav() { return false; }

    @Override
    boolean lightBars() { return true; }

    @Override
    View build() {
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Ui.col(c, R.color.splash_bg));
        LinearLayout center = Ui.vbox(c);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.logo_mark);
        logo.setContentDescription("Fastwell logo");
        center.addView(logo, Ui.lp(Ui.dp(c, 112), Ui.dp(c, 112)));
        TextView name = Ui.text(c, "Fastwell", 32, R.color.text, true);
        name.setTextColor(Ui.col(c, R.color.brand_navy));
        name.setPadding(0, Ui.dp(c, 18), 0, 0);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        // Explicit WRAP width: a vertical LinearLayout's default child width is MATCH_PARENT,
        // which left-aligned the name across the whole screen.
        center.addView(name, Ui.lp(Ui.WRAP, Ui.WRAP));
        // Tagline as three separate words around two peach dots, so all three always show.
        LinearLayout tag = Ui.hbox(c);
        tag.setGravity(Gravity.CENTER);
        String[] words = {"Eat", "Fast", "Train"};
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                View dot = new View(c);
                dot.setBackground(Ui.round(Ui.col(c, R.color.peach), Ui.dp(c, 3)));
                LinearLayout.LayoutParams dp = Ui.lp(Ui.dp(c, 6), Ui.dp(c, 6));
                dp.setMargins(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
                tag.addView(dot, dp);
            }
            TextView w = Ui.text(c, words[i], 15, R.color.text2, true);
            w.setTextColor(0xFF5B6488);
            w.setSingleLine(true);
            tag.addView(w, Ui.lp(Ui.WRAP, Ui.WRAP));
        }
        tag.setPadding(0, Ui.dp(c, 8), 0, 0);
        center.addView(tag, Ui.lp(Ui.WRAP, Ui.WRAP));
        root.addView(center, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER));

        logo.setScaleX(0.86f);
        logo.setScaleY(0.86f);
        logo.animate().scaleX(1f).scaleY(1f).setDuration(500).setInterpolator(new DecelerateInterpolator(2f)).start();
        name.setAlpha(0f);
        name.setTranslationY(Ui.dp(c, 8));
        name.animate().alpha(1f).translationY(0).setStartDelay(150).setDuration(450).start();
        tag.setAlpha(0f);
        tag.animate().alpha(1f).setStartDelay(300).setDuration(450).start();
        return root;
    }
}
