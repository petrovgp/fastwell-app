package app.fastwell;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen add flow opened from the Food screen: a meal bar, then quick log or an Open Food Facts search
 * for food, or volume cards for water. Input lives in fields rather than views, so a rebuild (e.g. on resume) keeps it.
 */
final class LogFoodScreen extends Screen {
    private static final int CUSTOM_WATER = FastingLogic.CUSTOM_WATER;
    /** Search results shown at most, and how many fit before the list scrolls. */
    private static final int MAX_RESULTS = 50, VISIBLE_RESULTS = 5;
    /** Macro fields top to bottom, as indexes into the {carbs, protein, fat} arrays. */
    private static final int[] MACRO_ROWS = {FastingLogic.PROTEIN, FastingLogic.CARBS, FastingLogic.FAT};
    private static final String[] MACRO_LABELS = {"Carbs g", "Protein g", "Fat g"};

    private int meal;
    private boolean searching;
    // Quick log
    private String name = "", kcalText = "";
    private final String[] gramText = {"", "", ""};
    private final double[] grams = new double[3];
    /** Macros least recently typed first; the first ones take up the difference to the calories. */
    private final int[] order = {FastingLogic.CARBS, FastingLogic.FAT, FastingLogic.PROTEIN};
    private boolean macroTyped;
    /** Grams when the focused total field got focus; each keystroke balances from here, so partly typed numbers don't wipe out macros. */
    private double[] snap;
    // Search
    private String query = "", status = "Searches your saved foods and Open Food Facts.", itemGrams = "";
    private final List<Db.Item> results = new ArrayList<Db.Item>();
    /** The first savedCount results come from the local food cache. */
    private int savedCount, searchId;
    private Db.Item item;
    // Water
    private int water = 1;
    private String customMl = "";

    private TextView title, statusView, note;
    private ScrollView resultsScroll;
    private final List<TextView> mealPills = new ArrayList<TextView>();
    private final List<Choice> modes = new ArrayList<Choice>(), waterCards = new ArrayList<Choice>();
    private Choice quickMode, searchMode;
    private LinearLayout foodPane, quickPane, searchPane, searchBox, resultsCard, resultsList, selectedBox, waterPane, totals;
    private EditText kcalField, customField;
    private final EditText[] gramFields = new EditText[3];
    private Button add;
    private boolean syncing;

    /** meal < 0 picks the meal from the time of day. */
    LogFoodScreen(MainActivity a, int meal) {
        super(a);
        this.meal = meal >= 0 ? meal
                : FastingLogic.mealForHour(Instant.ofEpochMilli(a.timeOnSelectedDay()).atZone(ZoneId.systemDefault()).getHour());
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        LinearLayout header = FastHistoryScreen.backHeader(a, "");
        header.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0);
        title = (TextView) header.getChildAt(1);
        header.addView(Ui.chip(c, Ui.dayLabel(a.selectedDay), R.color.eat));
        root.addView(header);

        LinearLayout col = Ui.column(c, 24);
        col.addView(mealBar());
        foodPane = Ui.vbox(c);
        foodPane.addView(modeRow());
        quickPane = quickPane();
        foodPane.addView(quickPane);
        searchPane = searchPane();
        foodPane.addView(searchPane);
        foodPane.addView(totalsCard());
        col.addView(foodPane);
        waterPane = waterPane();
        col.addView(waterPane);
        root.addView(Ui.scroll(c, col), Ui.lp(Ui.MATCH, 0, 1f));

        LinearLayout bottom = Ui.vbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        add = Ui.primary(c, "", R.color.eat);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { add(); }
        });
        bottom.addView(add, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));

        renderResults();
        showSelected();
        showMeal();
        return root;
    }

    // ---------- meal bar ----------

    /** Breakfast, lunch, dinner, snacks and water as pills in one rounded bar, like the date strip. */
    private View mealBar() {
        LinearLayout bar = Ui.hbox(c);
        int p = Ui.dp(c, 4);
        bar.setPadding(p, p, p, p);
        bar.setBackground(Ui.round(Ui.col(c, R.color.surface), Ui.dp(c, 24), Ui.col(c, R.color.outline), Ui.night(c) ? 0 : Ui.dp(c, 1)));
        mealPills.clear();
        for (int i = 0; i < FastingLogic.MEALS.length; i++) {
            final int m = i;
            TextView pill = Ui.text(c, FastingLogic.MEALS[i], 13, R.color.text2, true);
            pill.setGravity(Gravity.CENTER);
            pill.setSingleLine(true);
            pill.setPadding(Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10));
            pill.setClickable(true);
            pill.setFocusable(true);
            pill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    meal = m;
                    showMeal();
                }
            });
            mealPills.add(pill);
            bar.addView(pill, Ui.lp(Ui.WRAP, Ui.WRAP, 1f));
        }
        LinearLayout.LayoutParams lp = Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 4, 0, 16);
        bar.setLayoutParams(lp);
        return bar;
    }

    /** Restyles the meal pills in place and swaps the food and water panes. */
    private void showMeal() {
        for (int i = 0; i < mealPills.size(); i++) {
            TextView pill = mealPills.get(i);
            boolean on = i == meal;
            int fill = on ? Ui.alpha(Ui.col(c, R.color.eat), Ui.night(c) ? 0.22f : 0.14f) : 0;
            pill.setBackground(Ui.ripple(c, Ui.round(fill, Ui.dp(c, 20)), Ui.dp(c, 20)));
            pill.setTextColor(Ui.col(c, on ? R.color.eat : R.color.text2));
            pill.setContentDescription(FastingLogic.MEALS[i] + (on ? ", selected" : ""));
        }
        boolean isWater = meal == FastingLogic.WATER;
        title.setText(isWater ? "Log water" : "Log food");
        foodPane.setVisibility(isWater ? View.GONE : View.VISIBLE);
        waterPane.setVisibility(isWater ? View.VISIBLE : View.GONE);
        hideKeyboard();
        showMode();
    }

    // ---------- quick log or search ----------

    private View modeRow() {
        LinearLayout row = Ui.hbox(c);
        modes.clear();
        quickMode = Choice.toggle(c, "Quick log");
        searchMode = Choice.toggle(c, "Search foods");
        modes.add(quickMode);
        modes.add(searchMode);
        quickMode.onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) { pickMode(false); }
        });
        searchMode.onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) { pickMode(true); }
        });
        row.addView(quickMode.view, Ui.lp(0, Ui.WRAP, 1f));
        row.addView(Ui.hspace(c, 10));
        row.addView(searchMode.view, Ui.lp(0, Ui.WRAP, 1f));
        row.setLayoutParams(Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        return row;
    }

    private void pickMode(boolean search) {
        searching = search;
        hideKeyboard();
        showMode();
    }

    private void showMode() {
        Choice.select(modes, searching ? searchMode : quickMode);
        quickPane.setVisibility(searching ? View.GONE : View.VISIBLE);
        searchPane.setVisibility(searching ? View.VISIBLE : View.GONE);
        writeTotals();
        update();
    }

    private LinearLayout quickPane() {
        LinearLayout l = Ui.vbox(c);
        final EditText nameField = Ui.field(c, "Name (optional)", Ui.TXT);
        nameField.setText(name);
        nameField.addTextChangedListener(new WorkoutEditScreen.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                name = s.toString().trim();
                update();
            }
        });
        l.addView(nameField, Ui.lp(Ui.MATCH, Ui.WRAP));
        TextView hint = Ui.muted(c, "To log a zero-calorie drink like black coffee, give it a name and no calories. It keeps your fast.");
        hint.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), 0, Ui.dp(c, 4));
        l.addView(hint);
        return l;
    }

    private LinearLayout searchPane() {
        LinearLayout l = Ui.vbox(c);
        searchBox = Ui.vbox(c);
        LinearLayout row = Ui.hbox(c);
        final EditText q = Ui.field(c, "Search foods, e.g. greek yogurt", Ui.TXT);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setText(query);
        q.addTextChangedListener(new WorkoutEditScreen.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) { query = s.toString().trim(); }
        });
        row.addView(q, Ui.lp(0, Ui.WRAP, 1f));
        Button go = Ui.tonal(c, "Search", R.color.eat);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { search(); }
        });
        q.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { search(); return true; }
                return false;
            }
        });
        row.addView(go, Ui.lpm(c, Ui.WRAP, Ui.dp(c, 48), 6, 0, 0, 0));
        searchBox.addView(row);
        statusView = Ui.muted(c, "");
        statusView.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), 0, Ui.dp(c, 8));
        searchBox.addView(statusView);
        resultsCard = Ui.card(c);
        resultsCard.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        resultsList = Ui.vbox(c);
        resultsScroll = new CappedScroll(c, VISIBLE_RESULTS);
        // Scrolls inside the page's scroll view, which lets it have the drag.
        resultsScroll.setNestedScrollingEnabled(true);
        resultsScroll.addView(resultsList);
        resultsCard.addView(resultsScroll, Ui.lp(Ui.MATCH, Ui.WRAP));
        searchBox.addView(resultsCard);
        l.addView(searchBox);
        selectedBox = Ui.vbox(c);
        l.addView(selectedBox);
        return l;
    }

    private void search() {
        if (query.length() < 2) { Ui.toast(c, "Type at least 2 letters"); return; }
        hideKeyboard();
        final int id = ++searchId;
        results.clear();
        results.addAll(db.searchItems(query, 8));
        savedCount = results.size();
        status = "Searching Open Food Facts...";
        renderResults();
        Off.search(query, new Off.Callback() {
            @Override
            public void done(List<Db.Item> items, String error) {
                // Ignore answers to an older search, or arriving after the screen was closed.
                if (id != searchId || a.currentScreen() != LogFoodScreen.this) return;
                int n = Math.max(0, Math.min(items.size(), MAX_RESULTS - savedCount));
                if (error != null) status = error;
                else status = items.isEmpty() ? "No matches. Try other words, or use Quick log."
                        : n + " results from Open Food Facts (per 100 g)";
                results.addAll(items.subList(0, n));
                renderResults();
            }
        });
    }

    private void renderResults() {
        statusView.setText(status);
        resultsList.removeAllViews();
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) resultsList.addView(Ui.divider(c));
            resultsList.addView(resultRow(results.get(i), i < savedCount));
        }
        resultsScroll.scrollTo(0, 0);
        resultsCard.setVisibility(results.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private View resultRow(final Db.Item it, boolean saved) {
        LinearLayout r = Ui.hbox(c);
        r.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
        LinearLayout left = Ui.vbox(c);
        left.addView(Ui.text(c, it.name, 15, R.color.text, false));
        left.addView(Ui.muted(c, (saved ? "Saved  -  " : "") + (it.brand.isEmpty() ? "" : it.brand + "  -  ")
                + Ui.n0(it.kcal100) + " kcal / 100 g"));
        r.addView(left, Ui.lp(0, Ui.WRAP, 1f));
        r.addView(Ui.icon(c, R.drawable.ic_right, R.color.text2, 20));
        Ui.clickable(r, 8);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                item = it;
                itemGrams = Ui.plain(it.servingG > 0 ? it.servingG : 100);
                showSelected();
                writeTotals();
                update();
            }
        });
        return r;
    }

    /** The picked food with its amount field, in place of the search box and results. */
    private void showSelected() {
        searchBox.setVisibility(item == null ? View.VISIBLE : View.GONE);
        selectedBox.removeAllViews();
        if (item == null) return;
        LinearLayout card = Ui.card(c);
        LinearLayout head = Ui.hbox(c);
        head.addView(Ui.text(c, item.name, 17, R.color.text, true), Ui.lp(0, Ui.WRAP, 1f));
        Button change = Ui.flat(c, "Change", R.color.eat);
        change.setMinHeight(Ui.dp(c, 36));
        change.setMinimumHeight(Ui.dp(c, 36));
        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                item = null;
                showSelected();
                writeTotals();
                update();
            }
        });
        head.addView(change, Ui.lp(Ui.WRAP, Ui.dp(c, 36)));
        card.addView(head);
        card.addView(Ui.muted(c, (item.brand.isEmpty() ? "" : item.brand + "  -  ") + "per 100 g: " + Ui.n0(item.kcal100)
                + " kcal, P " + Ui.n1(item.p100) + ", C " + Ui.n1(item.c100) + ", F " + Ui.n1(item.f100)));
        EditText g = Ui.field(c, "Amount in grams", Ui.DEC);
        g.setText(itemGrams);
        g.addTextChangedListener(new WorkoutEditScreen.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                itemGrams = s.toString();
                writeTotals();
                update();
            }
        });
        card.addView(Ui.labeled(c, item.servingG > 0 ? "Grams (1 serving = " + Ui.n1(item.servingG) + " g)" : "Grams", g));
        selectedBox.addView(card);
    }

    // ---------- totals ----------

    /** Calories and macros: typed in quick log, worked out (and faded) for a searched food. */
    private View totalsCard() {
        LinearLayout l = Ui.vbox(c);
        l.addView(Ui.sectionTitle(c, "Total"));
        totals = Ui.card(c);
        totals.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 12));
        View.OnFocusChangeListener snapOnFocus = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) { snap = has ? grams.clone() : null; }
        };
        kcalField = Ui.field(c, "0", Ui.DEC);
        kcalField.setOnFocusChangeListener(snapOnFocus);
        kcalField.addTextChangedListener(new WorkoutEditScreen.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (syncing || searching) return;
                kcalText = s.toString();
                balance(-1);
            }
        });
        totals.addView(Ui.labeled(c, "Calories (kcal)", kcalField));
        LinearLayout row = Ui.hbox(c);
        for (int r = 0; r < MACRO_ROWS.length; r++) {
            final int m = MACRO_ROWS[r];
            EditText f = Ui.field(c, "0", Ui.DEC);
            f.setOnFocusChangeListener(snapOnFocus);
            f.addTextChangedListener(new WorkoutEditScreen.SimpleWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (syncing || searching) return;
                    gramText[m] = s.toString();
                    macroTyped = true;
                    balance(m);
                }
            });
            gramFields[m] = f;
            row.addView(Ui.labeled(c, MACRO_LABELS[m], f), Ui.lp(0, Ui.WRAP, 1f));
        }
        totals.addView(row);
        note = Ui.muted(c, "");
        note.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), 0, 0);
        totals.addView(note);
        l.addView(totals);
        return l;
    }

    /**
     * After the calories (changed = -1) or a macro was typed, guesses the other macros so they add up to
     * the calories, the least recently typed first, like the macro split dial on the daily target step.
     */
    private void balance(int changed) {
        if (snap == null) snap = grams.clone();
        double[] g = snap.clone();
        if (changed >= 0) g[changed] = Ui.num(gramText[changed], 0);
        double kcal = Ui.num(kcalText, 0);
        if (changed >= 0) {
            // Without calories there's nothing to match, but the typed macro still becomes the most recent.
            FastingLogic.balanceGrams(kcal > 0 ? kcal : FastingLogic.macroKcal(g), g, changed, order);
        } else if (macroTyped && kcal > 0) {
            FastingLogic.balanceGrams(kcal, g, -1, order);
        }
        for (int m = 0; m < 3; m++) {
            if (m == changed) continue;
            g[m] = Math.round(g[m] * 10) / 10.0;
            if (g[m] != grams[m]) gramText[m] = Ui.plain(g[m]);
        }
        System.arraycopy(g, 0, grams, 0, 3);
        syncing = true;
        for (int m = 0; m < 3; m++) if (m != changed) gramFields[m].setText(gramText[m]);
        syncing = false;
        update();
    }

    /** Fills the total fields from quick log or the searched food, and fades them when they can't be edited. */
    private void writeTotals() {
        double[] t = searchTotals();
        syncing = true;
        if (searching) {
            kcalField.setText(t == null ? "" : Ui.n0(t[0]));
            for (int m = 0; m < 3; m++) gramFields[m].setText(t == null ? "" : Ui.plain(t[m + 1]));
        } else {
            kcalField.setText(kcalText);
            for (int m = 0; m < 3; m++) gramFields[m].setText(gramText[m]);
        }
        syncing = false;
        kcalField.setEnabled(!searching);
        for (EditText f : gramFields) f.setEnabled(!searching);
        float alpha = searching ? 0.5f : 1f;
        kcalField.setAlpha(alpha);
        for (EditText f : gramFields) f.setAlpha(alpha);
    }

    /** {kcal, carbs, protein, fat} for the picked food and amount, or null without one. */
    private double[] searchTotals() {
        if (item == null) return null;
        double k = Math.max(0, Ui.num(itemGrams, 0)) / 100.0;
        return new double[] {item.kcal100 * k, item.c100 * k, item.p100 * k, item.f100 * k};
    }

    // ---------- water ----------

    private LinearLayout waterPane() {
        LinearLayout l = Ui.vbox(c);
        TextView sub = Ui.muted(c, "Water doesn't break your fast.");
        sub.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 12));
        l.addView(sub);
        waterCards.clear();
        for (int i = 0; i < FastingLogic.WATER_PRESETS.length; i++) {
            final int w = i;
            final Choice ch = Choice.card(c, waterBadge(i), i == CUSTOM_WATER ? "Custom" : FastingLogic.WATER_PRESETS[i],
                    i == CUSTOM_WATER ? "Type any amount" : FastingLogic.WATER_ML[i] + " ml");
            ch.onClick(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    water = w;
                    showWater();
                    if (w == CUSTOM_WATER) {
                        // After the field has been laid out, or the keyboard won't open for it.
                        customField.post(new Runnable() {
                            @Override
                            public void run() {
                                customField.requestFocus();
                                ((InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE))
                                        .showSoftInput(customField, InputMethodManager.SHOW_IMPLICIT);
                            }
                        });
                    } else {
                        hideKeyboard();
                    }
                }
            });
            waterCards.add(ch);
            l.addView(ch.view, Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        }
        customField = Ui.field(c, "Amount in ml", Ui.NUM);
        customField.setText(customMl);
        customField.addTextChangedListener(new WorkoutEditScreen.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                customMl = s.toString();
                update();
            }
        });
        l.addView(customField, Ui.lp(Ui.MATCH, Ui.WRAP));
        showWater();
        return l;
    }

    /** Drop in a tinted rounded square, like the fasting stage badges. */
    private View waterBadge(int i) {
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Ui.round(Ui.alpha(Ui.col(c, R.color.primary), Ui.night(c) ? 0.22f : 0.14f), Ui.dp(c, 12)));
        int size = i == CUSTOM_WATER ? 22 : 14 + 3 * i; // the drop grows with the volume
        badge.addView(Ui.icon(c, i == CUSTOM_WATER ? R.drawable.ic_add : R.drawable.ic_stage_drop, R.color.primary, size),
                new FrameLayout.LayoutParams(Ui.dp(c, size), Ui.dp(c, size), Gravity.CENTER));
        LinearLayout wrap = Ui.hbox(c);
        wrap.addView(badge, Ui.lp(Ui.dp(c, 44), Ui.dp(c, 44)));
        return wrap;
    }

    private void showWater() {
        Choice.select(waterCards, waterCards.get(water));
        customField.setVisibility(water == CUSTOM_WATER ? View.VISIBLE : View.GONE);
        if (add != null) update();
    }

    private int waterMl() {
        return water == CUSTOM_WATER ? (int) Math.round(Ui.num(customMl, 0)) : FastingLogic.WATER_ML[water];
    }

    // ---------- note and add button ----------

    /** Refreshes the note under the totals and the add button's label and enabled state. */
    private void update() {
        if (add == null || note == null) return;
        boolean ok;
        if (meal == FastingLogic.WATER) {
            int ml = waterMl();
            ok = ml > 0;
            add.setText(ok ? "Add " + Ui.n0(ml) + " ml of water" : "Add water");
        } else {
            double[] t = searchTotals();
            double fromMacros = FastingLogic.macroKcal(grams);
            double kcal = Ui.num(kcalText, 0);
            ok = searching ? t != null && Ui.num(itemGrams, 0) > 0 : kcal > 0 || fromMacros > 0 || !name.isEmpty();
            add.setText("Add to " + FastingLogic.MEALS[meal]);
            int noteColor = R.color.text2;
            String n;
            if (searching) {
                n = item == null ? "Pick a food to see its calories and macros." : "Worked out from the amount. Use Quick log to type your own.";
            } else if (kcalText.trim().isEmpty() && fromMacros > 0) {
                n = "Calories will be " + Ui.n0(fromMacros) + " kcal, from your macros.";
            } else if (kcal > 0 && fromMacros > kcal + 0.5) {
                n = "Your macros add up to " + Ui.n0(fromMacros) + " kcal, more than the calories.";
                noteColor = R.color.error;
            } else {
                n = "Type the calories, then any macro you know. The others are guessed to match.";
            }
            note.setText(n);
            note.setTextColor(Ui.col(c, noteColor));
            kcalField.setHint(!searching && fromMacros > 0 ? Ui.n0(fromMacros) : "0");
        }
        add.setEnabled(ok);
        add.setAlpha(ok ? 1f : 0.4f);
    }

    private void add() {
        if (meal == FastingLogic.WATER) {
            Db.Food f = new Db.Food();
            f.name = FastingLogic.WATER_PRESETS[water];
            f.amount = waterMl() + " ml";
            f.zeroCal = true;
            commit(f);
            return;
        }
        Db.Food f = new Db.Food();
        if (searching) {
            double g = Ui.num(itemGrams, 0);
            db.saveItem(item);
            f.name = item.brand.isEmpty() ? item.name : item.name + " (" + item.brand + ")";
            f.amount = Ui.n1(g) + " g";
            f.kcal = item.kcal100 * g / 100;
            f.protein = item.p100 * g / 100;
            f.carbs = item.c100 * g / 100;
            f.fat = item.f100 * g / 100;
        } else {
            f.carbs = grams[FastingLogic.CARBS];
            f.protein = grams[FastingLogic.PROTEIN];
            f.fat = grams[FastingLogic.FAT];
            f.kcal = Math.max(0, Ui.num(kcalText, FastingLogic.macroKcal(grams)));
            f.name = name.isEmpty() ? "Quick add" : name;
            f.zeroCal = f.kcal <= 0;
        }
        commit(f);
    }

    // ---------- saving ----------

    private void commit(final Db.Food f) {
        f.loggedAt = a.timeOnSelectedDay();
        f.day = Db.dayOf(f.loggedAt);
        f.meal = meal;
        final Db.Fast fast = db.activeFast();
        if (fast != null && !f.zeroCal && f.kcal > 0 && f.loggedAt > fast.startAt) {
            new AlertDialog.Builder(c)
                    .setTitle("This ends your fast")
                    .setMessage("You've fasted " + FastingLogic.dur(f.loggedAt - fast.startAt)
                            + ". End your fast at " + Ui.time(c, f.loggedAt) + "?")
                    .setPositiveButton("End fast", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            db.endFast(fast.id, f.loggedAt, 0, "");
                            save(f, fast);
                        }
                    })
                    .setNegativeButton("Log without ending", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) { save(f, null); }
                    })
                    .show();
        } else {
            save(f, null);
        }
    }

    private void save(final Db.Food f, final Db.Fast endedFast) {
        db.addFood(f);
        hideKeyboard();
        a.pop();
        Notifier.refresh(c);
        String msg = f.zeroCal ? "Logged " + f.name.toLowerCase() + " - your fast continues"
                : "Added " + Ui.n0(f.kcal) + " kcal to " + FastingLogic.MEALS[f.meal]
                + (endedFast != null ? " - fast ended at " + FastingLogic.dur(f.loggedAt - endedFast.startAt) : "");
        a.snack(msg, new Runnable() {
            @Override
            public void run() {
                db.deleteFood(f.id);
                if (endedFast != null && db.activeFast() == null) {
                    Db.Fast back = db.getFast(endedFast.id);
                    if (back != null) {
                        back.endAt = 0;
                        db.updateFast(back);
                    }
                }
            }
        });
    }

    private void hideKeyboard() {
        View focus = a.getCurrentFocus();
        if (focus == null) return;
        ((InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
        focus.clearFocus();
    }

    /** Scroll view no taller than its first {@code rows} rows (a vbox of rows with dividers between them). */
    private static final class CappedScroll extends ScrollView {
        private final int rows;

        CappedScroll(Context c, int rows) {
            super(c);
            this.rows = rows;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            LinearLayout list = getChildCount() > 0 ? (LinearLayout) getChildAt(0) : null;
            if (list == null || list.getChildCount() <= rows * 2 - 1) return;
            int h = 0;
            for (int i = 0; i < rows * 2 - 1; i++) { // rows and the dividers between them
                View v = list.getChildAt(i);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
                h += v.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            }
            setMeasuredDimension(getMeasuredWidth(), h);
        }
    }
}
