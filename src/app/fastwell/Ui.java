package app.fastwell;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/** Small view factory so screens stay readable without XML layouts. */
final class Ui {
    private Ui() {}

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static int col(Context c, int id) { return c.getColor(id); }

    static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static int alpha(int color, float a) { return (color & 0x00FFFFFF) | (Math.round(a * 255) << 24); }

    /** Darkens a color by scaling its RGB channels by f (0..1), keeping alpha. */
    static int shade(int color, float f) {
        int r = Math.round(((color >> 16) & 0xFF) * f), g = Math.round(((color >> 8) & 0xFF) * f), b = Math.round((color & 0xFF) * f);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static GradientDrawable round(int fill, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable round(int fill, float radiusPx, int stroke, int strokePx) {
        GradientDrawable g = round(fill, radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    static Drawable ripple(Context c, Drawable content, float radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(alpha(col(c, R.color.text), 0.12f)), content,
                round(0xFF000000, radiusPx));
    }

    /** Slides v in from the side dir points to (-1 left, 1 right) while fading it in; 0 does nothing. */
    static void slideIn(View v, int dir, int distanceDp) {
        if (dir == 0) return;
        v.setTranslationX(dir * dp(v.getContext(), distanceDp));
        v.setAlpha(0f);
        v.animate().translationX(0).alpha(1f).setDuration(220).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    static void clickable(View v, float radiusDp) {
        Drawable bg = v.getBackground();
        if (bg == null) bg = round(0x00000000, dp(v.getContext(), radiusDp));
        v.setBackground(ripple(v.getContext(), bg, dp(v.getContext(), radiusDp)));
        v.setClickable(true);
        v.setFocusable(true);
    }

    static TextView text(Context c, String s, float sp, int colorRes, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(col(c, colorRes));
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    static TextView body(Context c, String s) { return text(c, s, 15, R.color.text, false); }

    static TextView muted(Context c, String s) { return text(c, s, 13, R.color.text2, false); }

    static TextView big(Context c, String s, float sp, int colorRes) {
        TextView t = text(c, s, sp, colorRes, true);
        t.setFontFeatureSettings("tnum");
        return t;
    }

    static TextView sectionTitle(Context c, String s) {
        TextView t = text(c, s, 13, R.color.text2, true);
        t.setAllCaps(true);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(c, 4), dp(c, 12), 0, dp(c, 8));
        return t;
    }

    static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) { return new LinearLayout.LayoutParams(w, h, weight); }

    static LinearLayout.LayoutParams lpm(Context c, int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static View space(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(lp(MATCH, dp(c, heightDp)));
        return v;
    }

    static View hspace(Context c, int widthDp) {
        View v = new View(c);
        v.setLayoutParams(lp(dp(c, widthDp), 1));
        return v;
    }

    static View fill(Context c) {
        View v = new View(c);
        v.setLayoutParams(lp(0, 1, 1f));
        return v;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(col(c, R.color.outline));
        v.setLayoutParams(lpm(c, MATCH, Math.max(1, dp(c, 1) / 2 + 1), 0, 8, 0, 8));
        return v;
    }

    /** Surface card: 16dp corners, outline in light theme, 16dp padding. */
    static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        int pad = dp(c, 16);
        l.setPadding(pad, pad, pad, pad);
        l.setBackground(round(col(c, R.color.surface), dp(c, 16), col(c, R.color.outline), night(c) ? 0 : dp(c, 1)));
        l.setLayoutParams(lpm(c, MATCH, WRAP, 0, 0, 0, 12));
        return l;
    }

    static Button button(Context c, String label, int bg, int fg, int strokeColor) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(fg);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setBackground(ripple(c, round(bg, dp(c, 24), strokeColor, strokeColor == 0 ? 0 : dp(c, 1)), dp(c, 24)));
        return b;
    }

    static Button primary(Context c, String label, int colorRes) {
        return button(c, label, col(c, colorRes), onColor(c, colorRes), 0);
    }

    /** Text color for a filled button: dark ink on peach, light on blue/indigo. */
    static int onColor(Context c, int colorRes) {
        return col(c, colorRes == R.color.eat || colorRes == R.color.peach ? R.color.on_eat : R.color.on_primary);
    }

    static Button outline(Context c, String label, int colorRes) {
        return button(c, label, 0x00000000, col(c, colorRes), alpha(col(c, colorRes), 0.6f));
    }

    static Button tonal(Context c, String label, int colorRes) {
        return button(c, label, alpha(col(c, colorRes), night(c) ? 0.22f : 0.12f), col(c, colorRes), 0);
    }

    static Button flat(Context c, String label, int colorRes) {
        Button b = button(c, label, 0x00000000, col(c, colorRes), 0);
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        return b;
    }

    static FrameLayout.LayoutParams fabParams(Context c) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(WRAP, dp(c, 56), Gravity.BOTTOM | Gravity.END);
        p.setMargins(0, 0, dp(c, 16), dp(c, 16));
        return p;
    }

    static Button fab(Context c, String label, int colorRes) {
        Button b = fabShaped(c, "+  " + label, col(c, colorRes), onColor(c, colorRes));
        b.setLayoutParams(fabParams(c));
        return b;
    }

    /** A button with the FAB's shape and elevation but no layout params. */
    static Button fabShaped(Context c, String label, int bg, int fg) {
        Button b = button(c, label, bg, fg, 0);
        b.setBackground(ripple(c, round(bg, dp(c, 18)), dp(c, 18)));
        b.setElevation(dp(c, 6));
        return b;
    }

    static TextView chip(Context c, String s, int colorRes) {
        TextView t = text(c, s, 12, colorRes, true);
        t.setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3));
        t.setBackground(round(alpha(col(c, colorRes), night(c) ? 0.22f : 0.14f), dp(c, 12)));
        return t;
    }

    static ImageView icon(Context c, int res, int colorRes, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setImageTintList(ColorStateList.valueOf(col(c, colorRes)));
        v.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    static ImageView iconButton(Context c, int res, int colorRes, String description) {
        ImageView v = icon(c, res, colorRes, 24);
        int p = dp(c, 10);
        v.setPadding(p, p, p, p);
        v.setLayoutParams(lp(dp(c, 44), dp(c, 44)));
        v.setContentDescription(description);
        clickable(v, 22);
        return v;
    }

    static EditText field(Context c, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine(inputType != (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTextColor(col(c, R.color.text));
        e.setHintTextColor(col(c, R.color.text2));
        e.setSelectAllOnFocus(true);
        styleField(e);
        return e;
    }

    /** Outlined, rounded text field; the focused field gets a 2dp primary border. */
    static void styleField(EditText e) {
        Context c = e.getContext();
        android.graphics.drawable.StateListDrawable st = new android.graphics.drawable.StateListDrawable();
        int fill = night(c) ? alpha(col(c, R.color.text), 0.05f) : col(c, R.color.surface);
        st.addState(new int[] {android.R.attr.state_focused},
                round(fill, dp(c, 12), col(c, R.color.primary), dp(c, 2)));
        st.addState(new int[] {}, round(fill, dp(c, 12), alpha(col(c, R.color.text2), 0.45f), dp(c, 1)));
        int ix = dp(c, 3), iy = dp(c, 4);
        e.setBackground(new android.graphics.drawable.InsetDrawable(st, ix, iy, ix, iy));
        e.setPadding(dp(c, 14) + ix, dp(c, 12) + iy, dp(c, 14) + ix, dp(c, 12) + iy);
    }

    static final int NUM = InputType.TYPE_CLASS_NUMBER;
    static final int DEC = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    static final int TXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;

    /** Label above a field, for dialogs. */
    static LinearLayout labeled(Context c, String label, View field) {
        LinearLayout l = vbox(c);
        TextView t = muted(c, label);
        t.setPadding(dp(c, 4), dp(c, 8), 0, 0);
        l.addView(t);
        l.addView(field, lp(MATCH, WRAP));
        return l;
    }

    static LinearLayout dialogBody(Context c) {
        LinearLayout l = vbox(c);
        l.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), 0);
        return l;
    }

    static ScrollView scroll(Context c, View content) {
        ScrollView s = new ScrollView(c);
        s.setTag("main_scroll");
        s.setFillViewport(true);
        s.setClipToPadding(false);
        s.addView(content, new FrameLayout.LayoutParams(MATCH, WRAP));
        return s;
    }

    /** Standard screen column with padding; bottomPadDp leaves room for a FAB. */
    static LinearLayout column(Context c, int bottomPadDp) {
        LinearLayout l = vbox(c);
        l.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, bottomPadDp));
        return l;
    }

    static LinearLayout header(Context c, String title, String subtitle) {
        LinearLayout h = vbox(c);
        h.setPadding(dp(c, 4), dp(c, 12), dp(c, 4), dp(c, 12));
        TextView t = text(c, title, 28, R.color.text, true);
        h.addView(t);
        if (subtitle != null) h.addView(muted(c, subtitle));
        return h;
    }

    static double num(EditText e, double def) { return num(e.getText().toString(), def); }

    static double num(String text, double def) {
        try {
            String s = text.trim().replace(',', '.');
            return s.isEmpty() ? def : Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    static String n0(double v) { return String.format(Locale.getDefault(), "%,.0f", v); }

    static String n1(double v) {
        if (Math.abs(v - Math.round(v)) < 0.05) return String.format(Locale.getDefault(), "%,.0f", v);
        return String.format(Locale.getDefault(), "%,.1f", v);
    }

    /** Editable number: no grouping, '.' decimal, trailing .0 dropped. */
    static String plain(double v) {
        if (Math.abs(v - Math.round(v)) < 0.05) return String.valueOf(Math.round(v));
        return String.format(Locale.US, "%.1f", v);
    }

    static String time(Context c, long t) { return DateFormat.getTimeFormat(c).format(new Date(t)); }

    static String dateTime(Context c, long t) {
        return DateFormat.format("EEE d MMM, ", new Date(t)) + time(c, t);
    }

    static String dayLabel(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return "Today";
        if (d.equals(today.minusDays(1))) return "Yesterday";
        if (d.equals(today.plusDays(1))) return "Tomorrow";
        return d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()));
    }

    static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }
}
