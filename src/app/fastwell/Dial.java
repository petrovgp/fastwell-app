package app.fastwell;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Clock-style wheel with one or more columns side by side. Each editable column is dragged
 * vertically: drag up = higher value. A derived column follows another column (e.g. eating
 * hours = 24 - fasting hours), rolls along with it and is drawn faded because it can't be changed.
 */
final class Dial extends View {
    interface Listener { void onChange(int column, double value); }
    interface Derive { double of(double sourceValue); }
    interface Format { String of(double value); }

    static final class Col {
        final String label, unit;
        final double min, step;
        final int count, decimals, source;
        final Derive derive;
        Format format;
        float pos;
        int last;

        Col(String label, String unit, double min, double max, double step, int decimals, double value, int source, Derive derive) {
            this.label = label;
            this.unit = unit;
            this.min = min;
            this.step = step;
            this.count = (int) Math.round((max - min) / step) + 1;
            this.decimals = decimals;
            this.source = source;
            this.derive = derive;
            last = clampIndex((int) Math.round((value - min) / step));
            pos = last;
        }

        boolean editable() { return source < 0; }
        int clampIndex(int i) { return Math.max(0, Math.min(count - 1, i)); }
        int index() { return clampIndex(Math.round(pos)); }
        double valueAt(int i) { return min + i * step; }
        double value() { return valueAt(index()); }
    }

    private final List<Col> cols = new ArrayList<Col>();
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float itemH, headerH, slop;
    private final int ink, muted, primary;
    private Listener listener;
    private VelocityTracker vt;
    private float lastY, downY;
    private boolean dragging;
    private int active = -1;
    private int focusCol = 0;
    private ValueAnimator anim;

    Dial(Context c) {
        super(c);
        itemH = Ui.dp(c, 50);
        headerH = Ui.dp(c, 34);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        ink = Ui.col(c, R.color.text);
        muted = Ui.col(c, R.color.text2);
        primary = Ui.col(c, R.color.primary);
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        num.setTypeface(medium);
        num.setTextAlign(Paint.Align.LEFT);
        num.setFontFeatureSettings("tnum");
        unit.setTypeface(medium);
        unit.setTextAlign(Paint.Align.LEFT);
        head.setTextSize(Ui.dp(c, 12));
        head.setTypeface(medium);
        head.setTextAlign(Paint.Align.CENTER);
        head.setLetterSpacing(0.08f);
        band.setColor(Ui.alpha(primary, Ui.night(c) ? 0.22f : 0.12f));
        panel.setColor(Ui.alpha(ink, 0.04f));
        line.setColor(Ui.col(c, R.color.outline));
        line.setStrokeWidth(Ui.dp(c, 1));
        setFocusable(true);
    }

    /** Adds an editable column; returns its index. */
    int addColumn(String label, String unit, double min, double max, double step, int decimals, double value) {
        cols.add(new Col(label, unit, min, max, step, decimals, value, -1, null));
        describe();
        return cols.size() - 1;
    }

    /** Adds a read-only column computed from another column. */
    int addDerived(String label, String unit, int source, int decimals, Derive derive) {
        Col s = cols.get(source);
        Col d = new Col(label, unit, s.min, s.min + (s.count - 1) * s.step, s.step, decimals, s.value(), source, derive);
        cols.add(d);
        describe();
        return cols.size() - 1;
    }

    void setListener(Listener l) { listener = l; }

    /** Shows a column's values as text (e.g. a day offset as "Yesterday") instead of numbers. */
    void setFormat(int col, Format f) {
        cols.get(col).format = f;
        describe();
        invalidate();
    }

    double value(int col) {
        Col c = cols.get(col);
        if (!c.editable()) return c.derive.of(cols.get(c.source).value());
        return c.value();
    }

    void setValue(int col, double v) {
        Col c = cols.get(col);
        c.last = c.clampIndex((int) Math.round((v - c.min) / c.step));
        c.pos = c.last;
        describe();
        invalidate();
    }

    private String fmt(Col c, double v) {
        if (c.format != null) return c.format.of(v);
        if (c.decimals == 0 || Math.abs(v - Math.round(v)) < 1e-6) return String.valueOf(Math.round(v));
        return String.format(Locale.US, "%." + c.decimals + "f", v);
    }

    @Override
    protected void onMeasure(int w, int h) {
        // An exact height (e.g. from a layout weight) is honoured so dials can share a fixed screen.
        int height = MeasureSpec.getMode(h) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(h) : (int) (headerH + itemH * 4.4f);
        setMeasuredDimension(getDefaultSize(Ui.dp(getContext(), 300), w), height);
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = cols.size();
        if (n == 0) return;
        float w = getWidth(), h = getHeight();
        float colW = w / n;
        float big = Ui.dp(getContext(), n >= 3 ? 26 : 34);
        float small = big - Ui.dp(getContext(), n >= 3 ? 10 : 14);
        unit.setTextSize(Ui.dp(getContext(), n >= 3 ? 12 : 14));
        r.set(0, 0, w, h);
        c.drawRoundRect(r, Ui.dp(getContext(), 20), Ui.dp(getContext(), 20), panel);
        float cy = headerH + (h - headerH) / 2f;
        r.set(Ui.dp(getContext(), 8), cy - itemH / 2, w - Ui.dp(getContext(), 8), cy + itemH / 2);
        c.drawRoundRect(r, Ui.dp(getContext(), 14), Ui.dp(getContext(), 14), band);

        for (int i = 0; i < n; i++) {
            Col col = cols.get(i);
            float cx = colW * i + colW / 2f;
            boolean faded = !col.editable();
            head.setColor(faded ? Ui.alpha(muted, 0.6f) : primary);
            c.drawText(col.label, cx, Ui.dp(getContext(), 22), head);
            if (i > 0) c.drawLine(colW * i, headerH + Ui.dp(getContext(), 8), colW * i, h - Ui.dp(getContext(), 14), line);

            Col src = faded ? cols.get(col.source) : col;
            // Center the number + unit pair on the column using the selected value's width.
            num.setTextSize(big);
            String centerText = faded ? fmt(col, col.derive.of(src.value())) : fmt(col, col.value());
            float unitW = col.unit.isEmpty() ? 0 : unit.measureText(col.unit) + Ui.dp(getContext(), 4);
            float numW = num.measureText(centerText);
            float left = cx - (numW + unitW) / 2f;
            float numCenter = left + numW / 2f;

            c.save();
            c.clipRect(colW * i, headerH, colW * (i + 1), h);
            int base = (int) Math.floor(src.pos);
            for (int k = base - 3; k <= base + 3; k++) {
                if (k < 0 || k >= src.count) continue;
                float d = k - src.pos;
                float dist = Math.min(Math.abs(d), 2.2f);
                float y = cy + d * itemH;
                float size = big - dist / 2.2f * (big - small);
                float a = Math.max(0, 1 - dist / 2.4f);
                num.setTextSize(size);
                double v = faded ? col.derive.of(src.valueAt(k)) : col.valueAt(k);
                String s = fmt(col, v);
                float tw = num.measureText(s);
                num.setColor(faded ? Ui.alpha(muted, a * 0.5f) : Ui.alpha(ink, a));
                c.drawText(s, numCenter - tw / 2f, y + size * 0.36f, num);
            }
            c.restore();
            if (unitW > 0) {
                unit.setColor(faded ? Ui.alpha(muted, 0.5f) : ink);
                c.drawText(col.unit, left + numW + Ui.dp(getContext(), 4), cy + big * 0.36f, unit);
            }
        }
    }

    private int columnAt(float x) {
        int n = cols.size();
        return Math.max(0, Math.min(n - 1, (int) (x / (getWidth() / (float) n))));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int col = columnAt(e.getX());
                if (!cols.get(col).editable()) return false; // derived columns are read-only
                if (anim != null) anim.cancel();
                active = col;
                focusCol = col;
                getParent().requestDisallowInterceptTouchEvent(true);
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                lastY = downY = y;
                dragging = false;
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (active < 0) return false;
                if (vt != null) vt.addMovement(e);
                if (!dragging && Math.abs(y - downY) > slop) dragging = true;
                if (dragging) {
                    Col c = cols.get(active);
                    c.pos -= (y - lastY) / itemH;
                    c.pos = Math.max(-0.4f, Math.min(c.count - 0.6f, c.pos));
                    notifyIfChanged(active);
                    invalidate();
                }
                lastY = y;
                return true;
            case MotionEvent.ACTION_UP: {
                if (active < 0) return false;
                Col c = cols.get(active);
                if (!dragging) {
                    float cy = headerH + (getHeight() - headerH) / 2f;
                    settle(active, c.clampIndex(Math.round(c.pos + (y - cy) / itemH)));
                } else {
                    float vy = 0;
                    if (vt != null) {
                        vt.addMovement(e);
                        vt.computeCurrentVelocity(1000);
                        vy = vt.getYVelocity();
                    }
                    settle(active, c.clampIndex(Math.round(c.pos - vy * 0.16f / itemH)));
                }
                recycle();
                active = -1;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (active >= 0) settle(active, cols.get(active).index());
                recycle();
                active = -1;
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private void recycle() {
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
    }

    private void settle(final int col, int target) {
        if (anim != null) anim.cancel();
        final Col c = cols.get(col);
        anim = ValueAnimator.ofFloat(c.pos, target);
        anim.setDuration((long) (200 + Math.min(450, Math.abs(target - c.pos) * 60)));
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                c.pos = (Float) va.getAnimatedValue();
                notifyIfChanged(col);
                invalidate();
            }
        });
        anim.start();
    }

    private void notifyIfChanged(int col) {
        Col c = cols.get(col);
        int i = c.index();
        if (i == c.last) return;
        c.last = i;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        describe();
        if (listener != null) listener.onChange(col, c.value());
    }

    private void describe() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            Col c = cols.get(i);
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.label.toLowerCase(Locale.getDefault())).append(' ').append(fmt(c, value(i))).append(' ').append(c.unit);
        }
        sb.append(". Swipe up or down to change.");
        setContentDescription(sb.toString());
    }

    private void step(int col, int delta) {
        Col c = cols.get(col);
        if (!c.editable()) return;
        settle(col, c.clampIndex(c.index() + delta));
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_UP) { step(focusCol, 1); return true; }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) { step(focusCol, -1); return true; }
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_LEFT) {
            int d = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            for (int i = focusCol + d; i >= 0 && i < cols.size(); i += d) {
                if (cols.get(i).editable()) { focusCol = i; return true; }
            }
        }
        return super.onKeyDown(code, e);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setScrollable(true);
        info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { step(focusCol, 1); return true; }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { step(focusCol, -1); return true; }
        return super.performAccessibilityAction(action, args);
    }
}
