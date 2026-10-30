package app.fastwell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Custom drawn views: the fasting ring, a thin progress bar, and a bar chart. */
final class Views {
    private Views() {}

    /** Progress ring. Progress above 1 draws a second lap in the success color. */
    static final class Ring extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint over = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private float progress;
        private float shown;
        private float[] ticks = new float[0];

        Ring(Context c, float strokeDp, int color) {
            super(c);
            float sw = Ui.dp(c, strokeDp);
            for (Paint p : new Paint[] {track, arc, over}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sw);
                p.setStrokeCap(Paint.Cap.ROUND);
            }
            track.setStrokeCap(Paint.Cap.BUTT);
            setColor(color);
            over.setColor(Ui.col(c, R.color.success));
            tick.setColor(Ui.col(c, R.color.text2));
            tick.setStyle(Paint.Style.FILL);
        }

        void setColor(int color) {
            arc.setColor(color);
            track.setColor(Ui.alpha(color, 0.14f));
            invalidate();
        }

        /** Stage marks as fractions of the goal (0..1). */
        void setTicks(float[] t) { ticks = t; invalidate(); }

        void setProgress(float p, boolean animate) {
            progress = Math.max(0f, p);
            if (!animate) shown = progress;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float sw = arc.getStrokeWidth();
            float size = Math.min(getWidth(), getHeight());
            float l = (getWidth() - size) / 2f + sw / 2f + Ui.dp(getContext(), 6);
            float t = (getHeight() - size) / 2f + sw / 2f + Ui.dp(getContext(), 6);
            box.set(l, t, l + size - sw - Ui.dp(getContext(), 12), t + size - sw - Ui.dp(getContext(), 12));
            c.drawArc(box, 0, 360, false, track);
            // Ease towards the target so the ring glides instead of jumping.
            if (Math.abs(shown - progress) > 0.001f) {
                shown += (progress - shown) * 0.25f;
                postInvalidateOnAnimation();
            } else {
                shown = progress;
            }
            float first = Math.min(shown, 1f);
            if (first > 0) c.drawArc(box, -90, Math.max(0.5f, 360f * first), false, arc);
            if (shown > 1f) c.drawArc(box, -90, 360f * Math.min(shown - 1f, 1f), false, over);
            float r = box.width() / 2f + sw / 2f + Ui.dp(getContext(), 4);
            float cx = box.centerX(), cy = box.centerY();
            for (float f : ticks) {
                if (f <= 0 || f >= 1) continue;
                double a = Math.toRadians(-90 + 360 * f);
                c.drawCircle((float) (cx + r * Math.cos(a)), (float) (cy + r * Math.sin(a)), Ui.dp(getContext(), 2.5f), tick);
            }
        }
    }

    /** Thin rounded progress bar. */
    static final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float frac;

        Bar(Context c, int color, float frac) {
            super(c);
            fill.setColor(color);
            track.setColor(Ui.alpha(color, 0.15f));
            this.frac = frac;
        }

        void set(float f, int color) {
            frac = f;
            fill.setColor(color);
            invalidate();
        }

        void setTrack(int color) {
            track.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight();
            r.set(0, 0, getWidth(), h);
            c.drawRoundRect(r, h / 2, h / 2, track);
            float f = Math.max(0, Math.min(1, frac));
            if (f > 0) {
                r.set(0, 0, Math.max(h, getWidth() * f), h);
                c.drawRoundRect(r, h / 2, h / 2, fill);
            }
        }
    }

    /** Vertical bar chart with x labels, an optional dashed target line and per-day raises above that line. */
    static final class Chart extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barHit = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint target = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint raise = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint raiseTop = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float[] values = new float[0];
        private float[] raised;
        private String[] labels = new String[0];
        private float targetValue;
        private boolean colorByTarget;

        Chart(Context c, int color) {
            super(c);
            bar.setColor(color);
            barHit.setColor(Ui.col(c, R.color.success));
            label.setColor(Ui.col(c, R.color.text2));
            label.setTextSize(Ui.dp(c, 11));
            label.setTextAlign(Paint.Align.CENTER);
            target.setColor(Ui.col(c, R.color.text2));
            target.setStyle(Paint.Style.STROKE);
            target.setStrokeWidth(Ui.dp(c, 1.25f));
            target.setPathEffect(new DashPathEffect(new float[] {Ui.dp(c, 4), Ui.dp(c, 4)}, 0));
            grid.setColor(Ui.col(c, R.color.outline));
            grid.setStrokeWidth(Ui.dp(c, 1));
        }

        /** colorByTarget: bars at or above the target draw in the success color (used for fasting). */
        void setData(float[] v, String[] l, float targetValue, boolean colorByTarget) {
            values = v;
            labels = l;
            this.targetValue = targetValue;
            this.colorByTarget = colorByTarget;
            invalidate();
        }

        /** Shows how far each day's target rises above the target line; call after setData with one value per day. */
        void setRaised(float[] extra, int color) {
            raised = extra;
            raise.setColor(Ui.alpha(color, 0.35f));
            raiseTop.setColor(color);
            invalidate();
        }

        private float raisedAt(int i) { return raised == null ? 0 : raised[i]; }

        @Override
        protected void onDraw(Canvas c) {
            int n = values.length;
            if (n == 0) return;
            float labelH = Ui.dp(getContext(), 18);
            float top = Ui.dp(getContext(), 6);
            float h = getHeight() - labelH - top;
            float max = targetValue;
            for (float v : values) max = Math.max(max, v);
            for (int i = 0; i < n; i++) max = Math.max(max, targetValue + raisedAt(i));
            if (max <= 0) max = 1;
            max *= 1.1f;
            float slot = getWidth() / (float) n;
            float bw = Math.min(slot * 0.6f, Ui.dp(getContext(), 28));
            float base = top + h;
            c.drawLine(0, base, getWidth(), base, grid);
            int step = n > 14 ? (int) Math.ceil(n / 7.0) : 1;
            for (int i = 0; i < n; i++) {
                float cx = slot * i + slot / 2;
                float bh = h * values[i] / max;
                if (values[i] > 0) {
                    bh = Math.max(bh, Ui.dp(getContext(), 3));
                    r.set(cx - bw / 2, base - bh, cx + bw / 2, base);
                    float rad = Math.min(bw / 2, Ui.dp(getContext(), 6));
                    boolean hit = colorByTarget && targetValue > 0 && values[i] >= targetValue;
                    c.drawRoundRect(r, rad, rad, hit ? barHit : bar);
                    if (bh > rad) c.drawRect(r.left, base - rad, r.right, base, hit ? barHit : bar);
                }
                if (targetValue > 0 && raisedAt(i) > 0) {
                    // Over the bar, so the raise still shows on days that ate into it.
                    float from = base - h * targetValue / max;
                    float to = Math.min(base - h * (targetValue + raisedAt(i)) / max, from - Ui.dp(getContext(), 2));
                    c.drawRect(cx - bw / 2, to, cx + bw / 2, from, raise);
                    c.drawRect(cx - bw / 2, to, cx + bw / 2, to + Ui.dp(getContext(), 2), raiseTop);
                }
                if (i % step == 0 || i == n - 1) c.drawText(labels[i], cx, getHeight() - Ui.dp(getContext(), 4), label);
            }
            if (targetValue > 0) {
                float y = base - h * targetValue / max;
                Path p = new Path();
                p.moveTo(0, y);
                p.lineTo(getWidth(), y);
                c.drawPath(p, target);
            }
        }
    }
}
