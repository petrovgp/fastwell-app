package app.fastwell;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits a list of exercises, one expanded row at a time. It works on its own copy of the list and hands
 * the cleaned result to the callback; the callback decides what happens next, including leaving the screen.
 */
final class RoutineEditScreen extends Screen {
    interface Done { void on(List<FastingLogic.Lift> lifts); }

    private final String title;
    private final List<FastingLogic.Lift> lifts = new ArrayList<FastingLogic.Lift>();
    private final String startPrint;
    private final long excludeWorkoutId;
    private final boolean needsOne;
    private final Done done;
    private final List<String> names;

    /** Index of the open row, or -1. */
    private int expanded = -1;
    private LinearLayout rowsBox;
    private Button doneBtn;
    private TextView hintView;
    /** The open row's dial, stopped before anything reads or redraws the lifts so a rolling value can't change them later. */
    private Dial openDial;

    RoutineEditScreen(MainActivity a, String title, List<FastingLogic.Lift> start, long excludeWorkoutId, boolean needsOne, Done done) {
        super(a);
        this.title = title;
        for (FastingLogic.Lift l : start) lifts.add(l.copy());
        this.startPrint = FastingLogic.routineFingerprint(lifts);
        this.excludeWorkoutId = excludeWorkoutId;
        this.needsOne = needsOne;
        this.done = done;
        this.names = db.liftNames();
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));
        LinearLayout header = backHeader(title);
        header.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0);
        root.addView(header);

        LinearLayout col = Ui.column(c, 24);
        rowsBox = Ui.vbox(c);
        col.addView(rowsBox);
        Button add = Ui.tonal(c, "Add exercise", R.color.train);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lifts.add(new FastingLogic.Lift());
                expanded = lifts.size() - 1;
                rebuildRows(true);
            }
        });
        col.addView(add, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        root.addView(Ui.scroll(c, col), Ui.lp(Ui.MATCH, 0, 1f));

        LinearLayout bottom = Ui.hbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        doneBtn = Ui.primary(c, "Done", R.color.train);
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
        bottom.addView(doneBtn, Ui.lp(0, Ui.dp(c, 56), 1f));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));

        rebuildRows(false);
        return root;
    }

    /** Redraws only the rows, so the scroll position stays; the keyboard is hidden unless the name should be typed into. */
    private void rebuildRows(boolean focusName) {
        stopDial();
        if (!focusName) hideKeyboard();
        rowsBox.removeAllViews();
        hintView = null;
        openDial = null;
        for (int i = 0; i < lifts.size(); i++) {
            rowsBox.addView(i == expanded ? openRow(i, focusName) : closedRow(i));
        }
        updateDone();
    }

    private View closedRow(final int i) {
        FastingLogic.Lift l = lifts.get(i);
        LinearLayout card = Ui.card(c);
        boolean blank = l.name.trim().isEmpty();
        card.addView(Ui.text(c, blank ? "No name yet" : l.name.trim(), 16, blank ? R.color.text2 : R.color.text, true));
        card.addView(Ui.muted(c, FastingLogic.liftLine(l)));
        Ui.clickable(card, 16);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expanded = i;
                rebuildRows(false);
            }
        });
        return card;
    }

    private View openRow(final int i, boolean focusName) {
        final FastingLogic.Lift l = lifts.get(i);
        LinearLayout card = Ui.card(c);

        final AutoCompleteTextView name = new AutoCompleteTextView(c);
        name.setHint("Exercise name");
        name.setInputType(Ui.TXT);
        name.setSingleLine(true);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTextColor(Ui.col(c, R.color.text));
        name.setHintTextColor(Ui.col(c, R.color.text2));
        Ui.styleField(name);
        name.setThreshold(1);
        name.setAdapter(new ArrayAdapter<String>(c, android.R.layout.simple_dropdown_item_1line, names));
        name.setText(l.name, false);
        name.addTextChangedListener(new Ui.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                l.name = s.toString();
                updateHint(l);
                updateDone();
            }
        });
        card.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP));

        hintView = Ui.muted(c, "");
        hintView.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), 0, 0);
        card.addView(hintView);
        updateHint(l);

        Dial d = new Dial(c);
        openDial = d;
        d.addColumn("SETS", "", FastingLogic.SETS_MIN, FastingLogic.SETS_MAX, 1, 0, l.sets);
        d.addColumn("REPS", "", FastingLogic.REPS_MIN, FastingLogic.REPS_MAX, 1, 0, l.reps);
        int kg = d.addColumn("KG", "", 0, FastingLogic.LIFT_KG_MAX, FastingLogic.LIFT_KG_STEP, 1, l.weight);
        d.setFormat(kg, new Dial.Format() {
            @Override
            public String of(double v) { return v <= 0 ? "Bodyweight" : Ui.plain(v); }
        });
        d.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) {
                if (column == 0) l.sets = (int) Math.round(value);
                else if (column == 1) l.reps = (int) Math.round(value);
                else l.weight = value;
            }
        });
        card.addView(d, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 176), 0, 12, 0, 0));

        LinearLayout top = Ui.hbox(c);
        top.addView(action("Move up", i > 0, new Runnable() {
            @Override
            public void run() { move(i, -1); }
        }), Ui.lp(0, Ui.dp(c, 48), 1f));
        top.addView(Ui.hspace(c, 8));
        top.addView(action("Move down", i < lifts.size() - 1, new Runnable() {
            @Override
            public void run() { move(i, 1); }
        }), Ui.lp(0, Ui.dp(c, 48), 1f));
        card.addView(top, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));

        LinearLayout bottom = Ui.hbox(c);
        bottom.addView(action("Duplicate", true, new Runnable() {
            @Override
            public void run() {
                lifts.add(i + 1, l.copy());
                expanded = i + 1;
                rebuildRows(false);
            }
        }), Ui.lp(0, Ui.dp(c, 48), 1f));
        bottom.addView(Ui.hspace(c, 8));
        bottom.addView(action("Remove", true, new Runnable() {
            @Override
            public void run() {
                lifts.remove(i);
                expanded = -1;
                rebuildRows(false);
            }
        }), Ui.lp(0, Ui.dp(c, 48), 1f));
        card.addView(bottom, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));

        if (focusName) {
            name.requestFocus();
            name.post(new Runnable() {
                @Override
                public void run() {
                    ((InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(name, 0);
                }
            });
        }
        return card;
    }

    private Button action(String label, boolean enabled, final Runnable run) {
        Button b = Ui.tonal(c, label, R.color.train);
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.4f);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { run.run(); }
        });
        return b;
    }

    private void move(int i, int by) {
        lifts.add(i + by, lifts.remove(i));
        expanded = i + by;
        rebuildRows(false);
    }

    private void updateHint(FastingLogic.Lift l) {
        if (hintView == null) return;
        String n = l.name.trim();
        FastingLogic.Lift last = n.isEmpty() ? null : db.lastLift(n, excludeWorkoutId);
        hintView.setVisibility(last == null ? View.GONE : View.VISIBLE);
        if (last != null) hintView.setText("Last time: " + FastingLogic.liftLine(last));
    }

    private void updateDone() {
        boolean ok = !needsOne;
        for (FastingLogic.Lift l : lifts) {
            if (!l.name.trim().isEmpty()) ok = true;
        }
        doneBtn.setEnabled(ok);
        doneBtn.setAlpha(ok ? 1f : 0.4f);
    }

    private void stopDial() {
        if (openDial != null) openDial.stop();
    }

    private void finish() {
        stopDial();
        hideKeyboard();
        List<FastingLogic.Lift> out = new ArrayList<FastingLogic.Lift>();
        for (FastingLogic.Lift l : lifts) {
            String n = l.name.trim();
            if (n.isEmpty()) continue;
            FastingLogic.Lift k = l.copy();
            k.name = n;
            out.add(k);
        }
        done.on(out);
    }

    @Override
    boolean onBack() {
        stopDial();
        if (FastingLogic.routineFingerprint(lifts).equals(startPrint)) return false;
        hideKeyboard();
        new AlertDialog.Builder(a)
                .setTitle("Discard changes?")
                .setNegativeButton("Keep editing", null)
                .setPositiveButton("Discard", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) { a.pop(); }
                })
                .show();
        return true;
    }
}
