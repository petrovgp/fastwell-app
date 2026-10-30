package app.fastwell;

import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen editor for a log from the Food screen. A food log can change its amount, which rescales the
 * calories and macros, and its meal; a water log can only be deleted. The back arrow cancels.
 */
final class EditFoodScreen extends Screen {
    private final Db.Food food;
    private final boolean isWater, byGrams;
    /** The logged amount, in grams or servings, that the calories and macros belong to. */
    private final double base;

    private int meal;
    private String amountText;

    private final List<TextView> mealPills = new ArrayList<TextView>();
    private TextView kcalVal, proteinVal, carbsVal, fatVal;
    private Button save;

    EditFoodScreen(MainActivity a, Db.Food f) {
        super(a);
        food = f;
        meal = f.meal;
        isWater = f.meal == FastingLogic.WATER;
        double g = tenth(FastingLogic.grams(f.amount));
        byGrams = g > 0;
        base = byGrams ? g : tenth(FastingLogic.servings(f.amount));
        amountText = Ui.plain(base);
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        LinearLayout header = FastHistoryScreen.backHeader(a, isWater ? "Edit water entry" : "Edit food entry");
        header.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0);
        root.addView(header);

        LinearLayout col = Ui.column(c, 24);
        if (isWater) {
            col.addView(amountCard());
        } else {
            col.addView(Ui.sectionTitle(c, "Category"));
            col.addView(LogFoodScreen.mealBar(c, FastingLogic.WATER, mealPills, new LogFoodScreen.MealPicked() {
                @Override
                public void picked(int m) {
                    meal = m;
                    hideKeyboard();
                    update();
                }
            }));
            col.addView(amountCard());
            col.addView(Ui.sectionTitle(c, "Total"));
            col.addView(totalsCard());
        }
        root.addView(Ui.scroll(c, col), Ui.lp(Ui.MATCH, 0, 1f));

        LinearLayout bottom = Ui.vbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        LinearLayout buttons = Ui.hbox(c);
        Button delete = Ui.tonal(c, "Delete", R.color.error);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { delete(); }
        });
        buttons.addView(delete, Ui.lp(0, Ui.dp(c, 56), 1f));
        buttons.addView(Ui.hspace(c, 12));
        save = Ui.primary(c, "Save", R.color.eat);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { save(); }
        });
        buttons.addView(save, Ui.lp(0, Ui.dp(c, 56), 1f));
        bottom.addView(buttons, Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));

        update();
        return root;
    }

    /** The name, which can't be changed, above the amount: grams or servings, or faded millilitres for water. */
    private View amountCard() {
        LinearLayout card = Ui.card(c);
        card.addView(Ui.text(c, food.name, 17, R.color.text, true));
        EditText amount = Ui.field(c, "0", Ui.DEC);
        if (isWater) {
            amount.setText(Ui.plain(FastingLogic.millilitres(food.amount)));
            amount.setEnabled(false);
            amount.setAlpha(0.5f);
            card.addView(Ui.labeled(c, "Millilitres", amount));
            return card;
        }
        amount.setText(amountText);
        amount.addTextChangedListener(new Ui.SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                amountText = s.toString();
                update();
            }
        });
        card.addView(Ui.labeled(c, byGrams ? "Grams" : "Servings", amount));
        return card;
    }

    /** Calories and macros, worked out from the amount. */
    private View totalsCard() {
        LinearLayout card = Ui.card(c);
        LinearLayout row = Ui.hbox(c);
        kcalVal = addTotal(row, "Calories");
        proteinVal = addTotal(row, "Protein");
        carbsVal = addTotal(row, "Carbs");
        fatVal = addTotal(row, "Fat");
        card.addView(row);
        TextView note = Ui.muted(c, "Worked out from the amount.");
        note.setPadding(0, Ui.dp(c, 8), 0, 0);
        card.addView(note);
        return card;
    }

    private TextView addTotal(LinearLayout row, String label) {
        LinearLayout box = Ui.vbox(c);
        box.addView(Ui.muted(c, label));
        TextView val = Ui.text(c, "", 16, R.color.text, true);
        box.addView(val);
        row.addView(box, Ui.lp(0, Ui.WRAP, 1f));
        return val;
    }

    /** The typed amount to one decimal, as it will be saved. */
    private double amount() { return tenth(Math.max(0, Ui.num(amountText, 0))); }

    private static double tenth(double v) { return Math.round(v * 10) / 10.0; }

    private boolean canSave() {
        if (isWater || amount() <= 0) return false;
        return amount() != base || meal != food.meal;
    }

    private void update() {
        if (!isWater) {
            LogFoodScreen.showMealPills(c, mealPills, meal);
            double k = amount() / base;
            kcalVal.setText(Ui.n0(food.kcal * k) + " kcal");
            proteinVal.setText(Ui.n1(food.protein * k) + " g");
            carbsVal.setText(Ui.n1(food.carbs * k) + " g");
            fatVal.setText(Ui.n1(food.fat * k) + " g");
        }
        boolean ok = canSave();
        save.setEnabled(ok);
        save.setAlpha(ok ? 1f : 0.4f);
    }

    private void save() {
        if (!canSave()) return;
        final Db.Food before = food.copy();
        before.id = food.id;
        double next = amount();
        // Moving a log keeps its amount, even one that isn't in grams or servings like "a cup".
        if (next != base) {
            double k = next / base;
            food.kcal *= k;
            food.protein *= k;
            food.carbs *= k;
            food.fat *= k;
            food.amount = Ui.plain(next) + (byGrams ? " g" : next == 1 ? " serving" : " servings");
        }
        food.meal = meal;
        db.updateFood(food);
        hideKeyboard();
        a.pop();
        a.snack("Entry updated", new Runnable() {
            @Override
            public void run() { db.updateFood(before); }
        });
    }

    private void delete() {
        db.deleteFood(food.id);
        hideKeyboard();
        a.pop();
        a.snack("Deleted " + food.name, new Runnable() {
            @Override
            public void run() { db.addFood(food.copy()); }
        });
    }
}
