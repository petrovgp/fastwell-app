package app.fastwell;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

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

    /** True when the screen background is light, so system bar icons should be dark. */
    boolean lightBars() { return !Ui.night(c); }

    /** Which bottom-nav tab is highlighted while this screen is shown. */
    int navTab() { return -1; }

    /** Screen opened by tapping the date strip's day label. */
    DayPickerScreen dayPicker() { return new DayPickerScreen(a, "Choose day", R.color.primary, null); }

    void hideKeyboard() {
        View focus = a.getCurrentFocus();
        if (focus == null) return;
        ((InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }
}
