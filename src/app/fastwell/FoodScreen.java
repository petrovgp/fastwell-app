package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Calorie journal for the selected day, grouped by meal, plus the add-food flow. */
final class FoodScreen extends Screen {

    private static final int DIAL_H = 48, DIAL_GAP = 12;
    private FrameLayout dial;

    FoodScreen(MainActivity a) { super(a); }

    @Override
    DayPickerScreen dayPicker() {
        return new DayPickerScreen(a, "Choose food day", R.color.eat, new DayPickerScreen.Summary() {
            @Override
            public String of(LocalDate d) {
                double ml = 0;
                int n = 0;
                for (Db.Food f : db.foodForDay(d.toString())) {
                    if (f.meal == FastingLogic.WATER) ml += millilitres(f.amount);
                    else n++;
                }
                if (n == 0 && ml == 0) return "Nothing logged";
                String s = n + (n == 1 ? " item" : " items") + "  -  " + Ui.n0(db.totalsForDay(d.toString())[0]) + " kcal";
                return ml > 0 ? s + "  -  " + Ui.n0(ml) + " ml water" : s;
            }
        });
    }

    @Override
    View build() {
        final FrameLayout root = new FrameLayout(c);
        LinearLayout col = Ui.column(c, 96);
        col.addView(Ui.header(c, "Food", "Calories and macros"));
        col.addView(a.dateStrip(true));
        // Everything for the day itself, so it can slide in when the day changes.
        LinearLayout body = Ui.vbox(c);
        col.addView(body);
        Ui.slideIn(body, a.daySlide, 48);

        String day = a.selectedDay.toString();
        List<Db.Food> foods = db.foodForDay(day);
        double[] t = db.totalsForDay(day);
        int target = prefs.kcalTarget();

        LinearLayout sum = Ui.card(c);
        LinearLayout big = Ui.hbox(c);
        big.setGravity(Gravity.BOTTOM);
        big.addView(Ui.big(c, Ui.n0(t[0]), 32, R.color.text));
        TextView of = Ui.muted(c, "  / " + Ui.n0(target) + " kcal");
        of.setPadding(0, 0, 0, Ui.dp(c, 6));
        big.addView(of, Ui.lp(0, Ui.WRAP, 1f));
        double left = target - t[0];
        TextView leftT = Ui.text(c, left >= 0 ? Ui.n0(left) + " left" : Ui.n0(-left) + " over", 14,
                left >= 0 ? R.color.text2 : R.color.error, true);
        leftT.setPadding(0, 0, 0, Ui.dp(c, 6));
        big.addView(leftT);
        sum.addView(big);
        sum.addView(new Views.Bar(c, Ui.col(c, t[0] > target ? R.color.error : R.color.eat), (float) (t[0] / target)),
                Ui.lpm(c, Ui.MATCH, Ui.dp(c, 10), 0, 8, 0, 12));
        sum.addView(TodayScreen.macroRow(a, "Protein", t[1], prefs.proteinTarget(), R.color.protein));
        sum.addView(TodayScreen.macroRow(a, "Carbs", t[2], prefs.carbsTarget(), R.color.carbs));
        sum.addView(TodayScreen.macroRow(a, "Fat", t[3], prefs.fatTarget(), R.color.fat));
        body.addView(sum);

        for (int m = 0; m < FastingLogic.MEALS.length; m++) {
            final int meal = m;
            double kcal = 0, ml = 0;
            for (Db.Food f : foods) {
                if (f.meal != m) continue;
                kcal += f.kcal;
                ml += millilitres(f.amount);
            }
            String total = m == FastingLogic.WATER ? (ml > 0 ? Ui.n0(ml) + " ml" : "") : (kcal > 0 ? Ui.n0(kcal) + " kcal" : "");
            LinearLayout card = Ui.card(c);
            card.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));

            // Title with its + at the end; the divider below only appears once something is logged.
            LinearLayout head = Ui.hbox(c);
            final ImageView chevron = Ui.icon(c, R.drawable.ic_right, R.color.text2, 18);
            head.addView(chevron, Ui.lpm(c, Ui.dp(c, 18), Ui.dp(c, 18), 6, 0, 0, 0));
            TextView title = Ui.sectionTitle(c, FastingLogic.MEALS[m] + (total.isEmpty() ? "" : "  -  " + total));
            title.setPadding(Ui.dp(c, 4), 0, 0, 0);
            head.addView(title, Ui.lp(0, Ui.WRAP, 1f));
            ImageView add = Ui.iconButton(c, R.drawable.ic_add, R.color.eat, "Add to " + FastingLogic.MEALS[m]);
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { openAdd(meal); }
            });
            head.addView(add);
            card.addView(head);

            final LinearLayout items = Ui.vbox(c);
            for (final Db.Food f : foods) {
                if (f.meal != m) continue;
                if (items.getChildCount() == 0) items.addView(Ui.divider(c));
                items.addView(foodRow(f), Ui.lpm(c, Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));
            }
            if (items.getChildCount() > 0) {
                card.addView(items);
                // Tapping the title row folds the logged items away; the choice is remembered per meal.
                boolean collapsed = prefs.mealCollapsed(m);
                items.setVisibility(collapsed ? View.GONE : View.VISIBLE);
                chevron.setRotation(collapsed ? 0 : 90);
                Ui.clickable(head, 10);
                head.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean fold = items.getVisibility() == View.VISIBLE;
                        prefs.setMealCollapsed(meal, fold);
                        items.setVisibility(fold ? View.GONE : View.VISIBLE);
                        chevron.animate().rotation(fold ? 0 : 90).setDuration(160).start();
                    }
                });
            } else {
                chevron.setVisibility(View.INVISIBLE);
            }
            body.addView(card);
        }
        root.addView(Ui.scroll(c, col), new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        final Button fab = Ui.fab(c, "Add food", R.color.eat);
        dial = null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dial != null) closeDial();
                else openDial(root, fab);
            }
        });
        root.addView(fab);
        return root;
    }

    private View foodRow(final Db.Food f) {
        // Compact single line on a faint highlight, which also separates it from its neighbours.
        LinearLayout r = Ui.hbox(c);
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        r.setBackground(Ui.round(Ui.alpha(Ui.col(c, R.color.text), 0.05f), Ui.dp(c, 10)));
        r.addView(Ui.text(c, f.name, 15, R.color.text, false), Ui.lp(0, Ui.WRAP, 1f));
        String value = f.meal == FastingLogic.WATER ? Ui.n0(millilitres(f.amount)) + " ml" : Ui.n0(f.kcal) + " kcal";
        TextView k = Ui.big(c, value, 15, R.color.text);
        k.setPadding(Ui.dp(c, 8), 0, 0, 0);
        r.addView(k);
        Ui.clickable(r, 8);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { editDialog(f); }
        });
        return r;
    }

    // ---------- category speed dial ----------

    /** Stack of FAB-sized category buttons that rises out of the FAB; tapping outside closes it. */
    private void openDial(FrameLayout root, Button fab) {
        dial = new FrameLayout(c);
        dial.setBackgroundColor(Ui.alpha(0xFF000000, 0.3f));
        dial.setAlpha(0f);
        dial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { closeDial(); }
        });
        LinearLayout list = Ui.vbox(c);
        int n = FastingLogic.MEALS.length, w = Math.round(fab.getWidth() * 0.9f), h = Ui.dp(c, DIAL_H);
        int bg = Ui.shade(Ui.col(c, R.color.eat), 0.82f);
        for (int i = 0; i < n; i++) {
            final int meal = i;
            Button b = Ui.fabShaped(c, FastingLogic.MEALS[i], bg, Ui.onColor(c, R.color.eat));
            b.setSingleLine(true);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeDial();
                    openAdd(meal);
                }
            });
            // Start stacked on the FAB, then rise into place; the bottom button moves first.
            b.setTranslationY(rise(i, n));
            b.setAlpha(0f);
            b.animate().translationY(0).alpha(1f).setStartDelay((n - 1 - i) * 30L).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
            list.addView(b, Ui.lpm(c, w, h, 0, 0, 0, DIAL_GAP));
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
        return Ui.dp(c, (n - 1 - i) * (DIAL_H + DIAL_GAP) + DIAL_GAP + DIAL_H / 2f + 56 / 2f); // 56 = FAB height
    }

    private void closeDial() {
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

    void openAdd(int meal) {
        if (meal == FastingLogic.WATER) waterDialog();
        else new AddFood(meal);
    }

    private void waterDialog() {
        final int[] amounts = {250, 500, 750, 1000};
        String[] labels = {"Glass  -  250 ml", "Bottle  -  500 ml", "Large bottle  -  750 ml", "1 litre"};
        new AlertDialog.Builder(c)
                .setTitle("Add water  -  " + Ui.dayLabel(a.selectedDay))
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) { commit(water(amounts[w]), FastingLogic.WATER, null); }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private static Db.Food water(int ml) {
        Db.Food f = new Db.Food();
        f.name = "Water";
        f.amount = ml + " ml";
        f.zeroCal = true;
        return f;
    }

    /** Volume of an amount like "250 ml", or 0 when it isn't in millilitres. */
    private static double millilitres(String amount) {
        String s = amount.trim();
        if (!s.endsWith("ml")) return 0;
        try {
            return Double.parseDouble(s.substring(0, s.length() - 2).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------- saving (shared by all add paths) ----------

    private void commit(final Db.Food f, int meal, final AlertDialog parent) {
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
                            save(f, fast, parent);
                        }
                    })
                    .setNegativeButton("Log without ending", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) { save(f, null, parent); }
                    })
                    .show();
        } else {
            save(f, null, parent);
        }
    }

    private void save(final Db.Food f, final Db.Fast endedFast, AlertDialog parent) {
        db.addFood(f);
        if (parent != null) parent.dismiss();
        Notifier.refresh(c);
        a.refresh();
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

    private RadioGroup mealPicker(int checked, int count) {
        RadioGroup g = new RadioGroup(c);
        g.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < count; i++) {
            RadioButton rb = new RadioButton(c);
            rb.setText(FastingLogic.MEALS[i]);
            rb.setTextSize(12);
            rb.setId(2000 + i);
            g.addView(rb, new RadioGroup.LayoutParams(0, Ui.WRAP, 1f));
        }
        g.check(2000 + checked);
        return g;
    }

    private static int mealOf(RadioGroup g, int def) {
        int id = g.getCheckedRadioButtonId();
        return id >= 2000 && id < 2000 + FastingLogic.MEALS.length ? id - 2000 : def;
    }

    // ---------- add dialog ----------

    private final class AddFood {
        final AlertDialog dlg;
        final RadioGroup meals;
        final FrameLayout area;
        final Button[] tabs = new Button[4];
        final int defaultMeal;

        AddFood(int mealArg) {
            long t = a.timeOnSelectedDay();
            defaultMeal = mealArg >= 0 ? mealArg
                    : FastingLogic.mealForHour(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).getHour());
            LinearLayout body = Ui.dialogBody(c);
            meals = mealPicker(defaultMeal, FastingLogic.WATER);
            body.addView(meals);
            LinearLayout tabRow = Ui.hbox(c);
            tabRow.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            String[] names = {"Quick", "Recent", "Search", "Barcode"};
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                Button b = Ui.flat(c, names[i], R.color.text2);
                b.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { select(idx); }
                });
                tabs[i] = b;
                tabRow.addView(b, Ui.lp(0, Ui.dp(c, 40), 1f));
            }
            body.addView(tabRow);
            area = new FrameLayout(c);
            body.addView(area, Ui.lp(Ui.MATCH, Ui.WRAP));
            dlg = new AlertDialog.Builder(c)
                    .setTitle("Add food  -  " + Ui.dayLabel(a.selectedDay))
                    .setView(body)
                    .setNegativeButton("Close", null)
                    .create();
            select(0);
            dlg.show();
        }

        int meal() { return mealOf(meals, defaultMeal); }

        void select(int i) {
            for (int k = 0; k < 4; k++) {
                boolean on = k == i;
                Button b = tabs[k];
                b.setTextColor(Ui.col(c, on ? R.color.eat : R.color.text2));
                b.setBackground(Ui.round(on ? Ui.alpha(Ui.col(c, R.color.eat), Ui.night(c) ? 0.22f : 0.14f) : 0, Ui.dp(c, 20)));
            }
            area.removeAllViews();
            View v = i == 0 ? quick() : i == 1 ? recent() : i == 2 ? search() : barcode();
            area.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        }

        View quick() {
            LinearLayout l = Ui.vbox(c);
            final EditText name = Ui.field(c, "Name (optional)", Ui.TXT);
            final EditText kcal = Ui.field(c, "Calories (kcal)", Ui.DEC);
            l.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP));
            l.addView(kcal, Ui.lp(Ui.MATCH, Ui.WRAP));
            LinearLayout macros = Ui.hbox(c);
            final EditText p = Ui.field(c, "Protein g", Ui.DEC);
            final EditText cb = Ui.field(c, "Carbs g", Ui.DEC);
            final EditText f = Ui.field(c, "Fat g", Ui.DEC);
            macros.addView(p, Ui.lp(0, Ui.WRAP, 1f));
            macros.addView(cb, Ui.lp(0, Ui.WRAP, 1f));
            macros.addView(f, Ui.lp(0, Ui.WRAP, 1f));
            l.addView(macros);
            final TextView hint = Ui.muted(c, "Leave calories empty to compute them from macros.");
            l.addView(hint);
            Button add = Ui.primary(c, "Add", R.color.eat);
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Db.Food food = new Db.Food();
                    food.protein = Ui.num(p, 0);
                    food.carbs = Ui.num(cb, 0);
                    food.fat = Ui.num(f, 0);
                    double fromMacros = food.protein * 4 + food.carbs * 4 + food.fat * 9;
                    food.kcal = Ui.num(kcal, fromMacros);
                    if (food.kcal <= 0) { Ui.toast(c, "Enter calories or macros"); return; }
                    String n = name.getText().toString().trim();
                    food.name = n.isEmpty() ? "Quick add" : n;
                    commit(food, meal(), dlg);
                }
            });
            l.addView(add, Ui.lpm(c, Ui.MATCH, Ui.dp(c, 48), 0, 8, 0, 12));
            l.addView(Ui.muted(c, "Zero-calorie drinks (keep your fast)"));
            LinearLayout drinks = Ui.hbox(c);
            drinks.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 4));
            String[] names = {"Water", "Black coffee", "Tea"};
            for (int i = 0; i < names.length; i++) {
                final String dn = names[i];
                Button b = Ui.tonal(c, dn, R.color.primary);
                b.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
                b.setTextSize(13);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (dn.equals("Water")) { commit(water(250), FastingLogic.WATER, dlg); return; }
                        Db.Food food = new Db.Food();
                        food.name = dn;
                        food.amount = "1 cup";
                        food.zeroCal = true;
                        commit(food, meal(), dlg);
                    }
                });
                LinearLayout.LayoutParams lp = Ui.lp(0, Ui.dp(c, 40), 1f);
                if (i > 0) lp.setMarginStart(Ui.dp(c, 6));
                drinks.addView(b, lp);
            }
            l.addView(drinks);
            return l;
        }

        View recent() {
            List<Db.Food> rec = db.recentFoods(40);
            LinearLayout list = Ui.vbox(c);
            if (rec.isEmpty()) {
                TextView t = Ui.muted(c, "Foods you log will appear here for one-tap re-adding.");
                t.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
                return t;
            }
            for (final Db.Food f : rec) {
                LinearLayout r = Ui.hbox(c);
                r.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
                LinearLayout left = Ui.vbox(c);
                left.addView(Ui.text(c, f.name, 15, R.color.text, false));
                left.addView(Ui.muted(c, (f.amount.isEmpty() ? "" : f.amount + "  -  ")
                        + (f.zeroCal ? "zero-calorie" : Ui.n0(f.kcal) + " kcal")));
                r.addView(left, Ui.lp(0, Ui.WRAP, 1f));
                r.addView(Ui.icon(c, R.drawable.ic_add, R.color.eat, 22));
                Ui.clickable(r, 8);
                r.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { commit(f.copy(), meal(), dlg); }
                });
                list.addView(r);
            }
            return capped(list);
        }

        View search() {
            LinearLayout l = Ui.vbox(c);
            LinearLayout row = Ui.hbox(c);
            final EditText q = Ui.field(c, "Search foods, e.g. greek yogurt", Ui.TXT);
            q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            row.addView(q, Ui.lp(0, Ui.WRAP, 1f));
            Button go = Ui.tonal(c, "Search", R.color.eat);
            row.addView(go, Ui.lp(Ui.WRAP, Ui.dp(c, 44)));
            l.addView(row);
            final TextView status = Ui.muted(c, "Searches your saved foods and Open Food Facts.");
            status.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
            l.addView(status);
            final LinearLayout results = Ui.vbox(c);
            l.addView(capped(results));
            final Runnable run = new Runnable() {
                @Override
                public void run() {
                    final String query = q.getText().toString().trim();
                    if (query.length() < 2) { Ui.toast(c, "Type at least 2 letters"); return; }
                    results.removeAllViews();
                    List<Db.Item> local = db.searchItems(query, 8);
                    for (Db.Item it : local) results.addView(itemRow(it, true));
                    status.setText("Searching Open Food Facts...");
                    Off.search(query, new Off.Callback() {
                        @Override
                        public void done(List<Db.Item> items, String error) {
                            if (!dlg.isShowing()) return;
                            if (error != null) { status.setText(error); return; }
                            status.setText(items.isEmpty() ? "No matches. Try other words, or use Quick add."
                                    : items.size() + " results from Open Food Facts (per 100 g)");
                            for (Db.Item it : items) results.addView(itemRow(it, false));
                        }
                    });
                }
            };
            go.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { run.run(); }
            });
            q.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) { run.run(); return true; }
                    return false;
                }
            });
            return l;
        }

        View barcode() {
            LinearLayout l = Ui.vbox(c);
            l.addView(Ui.muted(c, "Type the number printed under the barcode."));
            LinearLayout row = Ui.hbox(c);
            final EditText code = Ui.field(c, "e.g. 5449000000996", Ui.NUM);
            row.addView(code, Ui.lp(0, Ui.WRAP, 1f));
            Button go = Ui.tonal(c, "Look up", R.color.eat);
            row.addView(go, Ui.lp(Ui.WRAP, Ui.dp(c, 44)));
            l.addView(row);
            final TextView status = Ui.muted(c, "");
            status.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            l.addView(status);
            go.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String s = code.getText().toString().trim();
                    if (s.length() < 6) { Ui.toast(c, "That barcode looks too short"); return; }
                    Db.Item cached = db.itemByBarcode(s);
                    if (cached != null) { servingDialog(cached, AddFood.this); return; }
                    status.setText("Looking up " + s + "...");
                    Off.barcode(s, new Off.Callback() {
                        @Override
                        public void done(List<Db.Item> items, String error) {
                            if (!dlg.isShowing()) return;
                            if (error != null) status.setText(error);
                            else if (items.isEmpty()) status.setText("Not found in Open Food Facts. Use Quick add instead.");
                            else { status.setText(""); servingDialog(items.get(0), AddFood.this); }
                        }
                    });
                }
            });
            return l;
        }

        private View itemRow(final Db.Item it, boolean saved) {
            LinearLayout r = Ui.hbox(c);
            r.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
            LinearLayout left = Ui.vbox(c);
            left.addView(Ui.text(c, it.name, 15, R.color.text, false));
            left.addView(Ui.muted(c, (saved ? "Saved  -  " : "") + (it.brand.isEmpty() ? "" : it.brand + "  -  ")
                    + Ui.n0(it.kcal100) + " kcal / 100 g"));
            r.addView(left, Ui.lp(0, Ui.WRAP, 1f));
            r.addView(Ui.icon(c, R.drawable.ic_right, R.color.text2, 20));
            Ui.clickable(r, 8);
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { servingDialog(it, AddFood.this); }
            });
            return r;
        }

        private View capped(View list) {
            ScrollView s = new ScrollView(c);
            s.addView(list);
            s.setLayoutParams(new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(c, 300)));
            return s;
        }
    }

    private void servingDialog(final Db.Item it, final AddFood parent) {
        LinearLayout body = Ui.dialogBody(c);
        body.addView(Ui.text(c, it.name, 17, R.color.text, true));
        body.addView(Ui.muted(c, (it.brand.isEmpty() ? "" : it.brand + "  -  ") + "per 100 g: " + Ui.n0(it.kcal100) + " kcal, P "
                + Ui.n1(it.p100) + ", C " + Ui.n1(it.c100) + ", F " + Ui.n1(it.f100)));
        final EditText grams = Ui.field(c, "Amount in grams", Ui.DEC);
        grams.setText(Ui.plain(it.servingG > 0 ? it.servingG : 100));
        body.addView(Ui.labeled(c, it.servingG > 0 ? "Grams (1 serving = " + Ui.n1(it.servingG) + " g)" : "Grams", grams));
        final TextView preview = Ui.big(c, "", 20, R.color.text);
        preview.setPadding(0, Ui.dp(c, 8), 0, 0);
        body.addView(preview);
        final Runnable upd = new Runnable() {
            @Override
            public void run() {
                double g = Ui.num(grams, 0) / 100.0;
                preview.setText(Ui.n0(it.kcal100 * g) + " kcal   P " + Ui.n0(it.p100 * g) + "  C " + Ui.n0(it.c100 * g) + "  F " + Ui.n0(it.f100 * g));
            }
        };
        upd.run();
        grams.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c1, int a1) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c1) {}
            @Override public void afterTextChanged(Editable s) { upd.run(); }
        });
        new AlertDialog.Builder(c)
                .setTitle("How much?")
                .setView(body)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double g = Ui.num(grams, 0);
                        if (g <= 0) { Ui.toast(c, "Enter an amount"); return; }
                        db.saveItem(it);
                        Db.Food f = new Db.Food();
                        f.name = it.brand.isEmpty() ? it.name : it.name + " (" + it.brand + ")";
                        f.amount = Ui.n1(g) + " g";
                        f.kcal = it.kcal100 * g / 100;
                        f.protein = it.p100 * g / 100;
                        f.carbs = it.c100 * g / 100;
                        f.fat = it.f100 * g / 100;
                        commit(f, parent.meal(), parent.dlg);
                    }
                })
                .setNegativeButton("Back", null)
                .show();
    }

    // ---------- edit ----------

    private void editDialog(final Db.Food f) {
        LinearLayout body = Ui.dialogBody(c);
        final EditText name = Ui.field(c, "Name", Ui.TXT);
        name.setText(f.name);
        final EditText amount = Ui.field(c, "Amount (optional)", Ui.TXT);
        amount.setText(f.amount);
        final EditText kcal = Ui.field(c, "kcal", Ui.DEC);
        kcal.setText(Ui.plain(f.kcal));
        final EditText p = Ui.field(c, "Protein", Ui.DEC);
        p.setText(Ui.plain(f.protein));
        final EditText cb = Ui.field(c, "Carbs", Ui.DEC);
        cb.setText(Ui.plain(f.carbs));
        final EditText fat = Ui.field(c, "Fat", Ui.DEC);
        fat.setText(Ui.plain(f.fat));
        body.addView(Ui.labeled(c, "Name", name));
        body.addView(Ui.labeled(c, "Amount", amount));
        body.addView(Ui.labeled(c, "Calories", kcal));
        LinearLayout m = Ui.hbox(c);
        m.addView(Ui.labeled(c, "Protein g", p), Ui.lp(0, Ui.WRAP, 1f));
        m.addView(Ui.labeled(c, "Carbs g", cb), Ui.lp(0, Ui.WRAP, 1f));
        m.addView(Ui.labeled(c, "Fat g", fat), Ui.lp(0, Ui.WRAP, 1f));
        body.addView(m);
        final RadioGroup meals = mealPicker(f.meal, FastingLogic.MEALS.length);
        body.addView(meals);
        ScrollView sv = new ScrollView(c);
        sv.addView(body);
        new AlertDialog.Builder(c)
                .setTitle("Edit entry")
                .setView(sv)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        f.name = n.isEmpty() ? f.name : n;
                        f.amount = amount.getText().toString().trim();
                        f.kcal = Math.max(0, Ui.num(kcal, f.kcal));
                        f.protein = Math.max(0, Ui.num(p, 0));
                        f.carbs = Math.max(0, Ui.num(cb, 0));
                        f.fat = Math.max(0, Ui.num(fat, 0));
                        f.meal = mealOf(meals, f.meal);
                        db.updateFood(f);
                        a.refresh();
                    }
                })
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        db.deleteFood(f.id);
                        a.refresh();
                        a.snack("Deleted " + f.name, new Runnable() {
                            @Override
                            public void run() { db.addFood(f.copy()); }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
