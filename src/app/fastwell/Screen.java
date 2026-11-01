package app.fastwell;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A screen builds its whole view tree from the database; MainActivity rebuilds it after changes. */
abstract class Screen {
    final MainActivity a;
    final Context c;
    final Db db;
    final Prefs prefs;

    Screen(MainActivity a) {
        this.a = a;
        this.c = a;
        this.db = a.db;
        this.prefs = a.prefs;
    }

    abstract View build();

    /** Called once a second while visible. */
    void tick() {}

    boolean showsNav() { return true; }

    /** Return true when the screen handled back itself, so MainActivity doesn't leave it. */
    boolean onBack() { return false; }

    /** True when the screen background is light, so system bar icons should be dark. */
    boolean lightBars() { return !Ui.night(c); }

    /** Which bottom-nav tab is highlighted while this screen is shown. */
    int navTab() { return -1; }

    /** Screen opened by tapping the date strip's day label. */
    DayPickerScreen dayPicker() { return new DayPickerScreen(a, "Choose day", R.color.primary, null); }

    /** Back arrow and title; a screen with a blank title fills it in later through the header's second child. */
    LinearLayout backHeader(String title) {
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

    void hideKeyboard() {
        View focus = a.getCurrentFocus();
        if (focus == null) return;
        ((InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }
}
