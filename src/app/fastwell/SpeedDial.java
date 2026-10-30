package app.fastwell;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Stack of FAB-sized buttons that rises out of a FAB when it is tapped; tapping outside closes it. */
final class SpeedDial {

    interface Pick { void on(int index); }

    private static final int H = 48, GAP = 12;

    private final Context c;
    private final FrameLayout root;
    private final Button fab;
    private final int colorRes;
    private final String[] labels;
    private final Pick pick;
    private FrameLayout dial;

    /** Takes over the FAB's click; the FAB must be a direct child of root. */
    SpeedDial(FrameLayout root, Button fab, int colorRes, String[] labels, Pick pick) {
        this.c = root.getContext();
        this.root = root;
        this.fab = fab;
        this.colorRes = colorRes;
        this.labels = labels;
        this.pick = pick;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dial != null) close();
                else open();
            }
        });
    }

    private void open() {
        dial = new FrameLayout(c);
        dial.setBackgroundColor(Ui.alpha(0xFF000000, 0.3f));
        dial.setAlpha(0f);
        dial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { close(); }
        });
        LinearLayout list = Ui.vbox(c);
        int n = labels.length, w = Math.round(fab.getWidth() * 0.9f), h = Ui.dp(c, H);
        int bg = Ui.shade(Ui.col(c, colorRes), 0.82f);
        for (int i = 0; i < n; i++) {
            final int index = i;
            Button b = Ui.fabShaped(c, labels[i], bg, Ui.onColor(c, colorRes));
            b.setSingleLine(true);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    close();
                    pick.on(index);
                }
            });
            // Start stacked on the FAB, then rise into place; the bottom button moves first.
            b.setTranslationY(rise(i, n));
            b.setAlpha(0f);
            b.animate().translationY(0).alpha(1f).setStartDelay((n - 1 - i) * 30L).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
            list.addView(b, Ui.lpm(c, w, h, 0, 0, 0, GAP));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.END);
        // Centred over the FAB; the last button's own bottom margin leaves the gap above the FAB.
        lp.setMargins(0, 0, Ui.dp(c, 16) + (fab.getWidth() - w) / 2, Ui.dp(c, 16) + fab.getHeight());
        dial.addView(list, lp);
        // Below the FAB so it stays on top and can close the dial.
        root.addView(dial, root.indexOfChild(fab), new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        dial.animate().alpha(1f).setDuration(200).start();
    }

    /** Downward offset that puts dial button i (of n, top first) centred on the FAB. */
    private int rise(int i, int n) {
        return Ui.dp(c, (n - 1 - i) * (H + GAP) + GAP + H / 2f + 56 / 2f); // 56 = FAB height
    }

    private void close() {
        final FrameLayout d = dial;
        if (d == null) return;
        dial = null;
        d.setClickable(false);
        LinearLayout list = (LinearLayout) d.getChildAt(0);
        int n = list.getChildCount();
        for (int i = 0; i < n; i++) {
            View b = list.getChildAt(i);
            b.setEnabled(false);
            b.animate().translationY(rise(i, n)).alpha(0f).setStartDelay(0).setDuration(360).start();
        }
        d.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (d.getParent() != null) ((FrameLayout) d.getParent()).removeView(d);
            }
        }).start();
    }
}
