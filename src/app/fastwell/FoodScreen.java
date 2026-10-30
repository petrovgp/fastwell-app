package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/** Calorie journal for the selected day, grouped by meal; adding opens LogFoodScreen. */
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
        a.push(new LogFoodScreen(a, meal));
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
