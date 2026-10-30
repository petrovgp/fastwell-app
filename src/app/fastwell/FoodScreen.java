package app.fastwell;

import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/** Calorie journal for the selected day, grouped by meal; adding opens LogFoodScreen, tapping a log opens EditFoodScreen. */
final class FoodScreen extends Screen {

    FoodScreen(MainActivity a) { super(a); }

    @Override
    DayPickerScreen dayPicker() {
        return new DayPickerScreen(a, "Choose food day", R.color.eat, new DayPickerScreen.Summary() {
            @Override
            public String of(LocalDate d) {
                double ml = 0;
                int n = 0;
                for (Db.Food f : db.foodForDay(d.toString())) {
                    if (f.meal == FastingLogic.WATER) ml += FastingLogic.millilitres(f.amount);
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
        FrameLayout root = new FrameLayout(c);
        LinearLayout col = Ui.column(c, 96);
        col.addView(Ui.header(c, "Food", "Calories and macros"));
        col.addView(a.dateStrip(true));
        // Everything for the day itself, so it can slide in when the day changes.
        LinearLayout body = Ui.vbox(c);
        col.addView(body);
        Ui.slideIn(body, a.daySlide, 48);

        String day = a.selectedDay.toString();
        List<Db.Food> foods = db.foodForDay(day);

        body.addView(TodayScreen.summaryCard(a, day));

        for (int m = 0; m < FastingLogic.MEALS.length; m++) {
            final int meal = m;
            double kcal = 0, ml = 0;
            for (Db.Food f : foods) {
                if (f.meal != m) continue;
                kcal += f.kcal;
                ml += FastingLogic.millilitres(f.amount);
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
        Button fab = Ui.fab(c, "Add food", R.color.eat);
        root.addView(fab);
        new SpeedDial(root, fab, R.color.eat, FastingLogic.MEALS, new SpeedDial.Pick() {
            @Override
            public void on(int meal) { openAdd(meal); }
        });
        return root;
    }

    private View foodRow(final Db.Food f) {
        // Compact single line on a faint highlight, which also separates it from its neighbours.
        LinearLayout r = Ui.hbox(c);
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        r.setBackground(Ui.round(Ui.alpha(Ui.col(c, R.color.text), 0.05f), Ui.dp(c, 10)));
        r.addView(Ui.text(c, f.name, 15, R.color.text, false), Ui.lp(0, Ui.WRAP, 1f));
        String value = f.meal == FastingLogic.WATER ? Ui.n0(FastingLogic.millilitres(f.amount)) + " ml" : Ui.n0(f.kcal) + " kcal";
        TextView k = Ui.big(c, value, 15, R.color.text);
        k.setPadding(Ui.dp(c, 8), 0, 0, 0);
        r.addView(k);
        Ui.clickable(r, 8);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.push(new EditFoodScreen(a, f)); }
        });
        return r;
    }

    void openAdd(int meal) {
        a.push(new LogFoodScreen(a, meal));
    }
}
