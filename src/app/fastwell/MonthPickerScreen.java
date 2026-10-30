package app.fastwell;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Full-screen month chooser for the Fasting history calendar: a month / year dial that can't go past this month. */
final class MonthPickerScreen extends Screen {
    private static final int MONTH = 0, YEAR = 1;

    /** What the history holds for a month, shown under the picked month (e.g. "12 fasts"). */
    interface Summary { String of(YearMonth m); }

    /** Receives the chosen month; the picker pops itself afterwards. */
    interface Picked { void on(YearMonth m); }

    private final Summary summary;
    private final Picked picked;
    private final int firstYear;
    private YearMonth month;

    private Dial dial;
    private TextView monthVal, detail;
    private Button thisMonth;

    MonthPickerScreen(MainActivity a, YearMonth month, int firstYear, Summary summary, Picked picked) {
        super(a);
        this.month = month;
        this.summary = summary;
        this.picked = picked;
        this.firstYear = Math.min(firstYear, Math.min(month.getYear(), YearMonth.now().getYear() - DayPickerScreen.YEARS_BACK));
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    int navTab() { return MainActivity.TAB_FAST; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        LinearLayout col = Ui.vbox(c);
        col.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), 0);
        col.addView(FastHistoryScreen.backHeader(a, "Choose month"));
        col.addView(summaryCard());

        dial = new Dial(c);
        dial.addColumn("MONTH", "", 1, 12, 1, 0, month.getMonthValue());
        dial.setFormat(MONTH, new Dial.Format() {
            @Override
            public String of(double v) { return Month.of((int) v).getDisplayName(TextStyle.SHORT, Locale.getDefault()); }
        });
        dial.addColumn("YEAR", "", firstYear, YearMonth.now().getYear(), 1, 0, month.getYear());
        dial.setFormat(YEAR, new Dial.Format() {
            @Override
            public String of(double v) { return String.valueOf((int) v); }
        });
        dial.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) { readDial(); }
        });
        dial.setOnSettle(new Runnable() {
            @Override
            public void run() { syncDial(); }
        });
        col.addView(dial, Ui.lp(Ui.MATCH, 0, 1f));
        root.addView(col, Ui.lp(Ui.MATCH, 0, 1f));

        LinearLayout bottom = Ui.hbox(c);
        bottom.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 16));
        bottom.setBackgroundColor(Ui.col(c, R.color.bg));
        thisMonth = Ui.outline(c, "This month", R.color.primary);
        thisMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { show(YearMonth.now()); }
        });
        bottom.addView(thisMonth, Ui.lpm(c, 0, Ui.dp(c, 56), 0, 0, 12, 0));
        ((LinearLayout.LayoutParams) thisMonth.getLayoutParams()).weight = 1f;
        Button go = Ui.primary(c, "Show this month", R.color.primary);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                picked.on(month);
                a.pop();
            }
        });
        bottom.addView(go, Ui.lp(0, Ui.dp(c, 56), 2f));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));
        update();
        return root;
    }

    private View summaryCard() {
        LinearLayout card = Ui.card(c);
        card.addView(Ui.muted(c, "Selected month"));
        monthVal = Ui.text(c, "", 18, R.color.text, true);
        monthVal.setSingleLine(true);
        monthVal.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(monthVal);
        detail = Ui.text(c, "", 13, R.color.primary, true);
        detail.setPadding(0, Ui.dp(c, 8), 0, 0);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(detail);
        return card;
    }

    /** Reads the dial, turning a future pick (a later month of this year) into this month. */
    private void readDial() {
        YearMonth m = YearMonth.of((int) dial.value(YEAR), (int) dial.value(MONTH));
        YearMonth now = YearMonth.now();
        month = m.isAfter(now) ? now : m;
        update();
    }

    private void show(YearMonth m) {
        // A still-rolling column would otherwise keep moving and drag the month away again.
        dial.stop();
        month = m;
        syncDial();
        update();
    }

    /** Moves any column that disagrees with the picked month onto it; done once the wheel stops so it never jumps mid-drag. */
    private void syncDial() {
        if ((int) dial.value(MONTH) != month.getMonthValue()) dial.setValue(MONTH, month.getMonthValue());
        if ((int) dial.value(YEAR) != month.getYear()) dial.setValue(YEAR, month.getYear());
    }

    private void update() {
        if (monthVal == null) return;
        monthVal.setText(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())));
        detail.setText(summary != null ? summary.of(month) : "");
        detail.setVisibility(summary != null ? View.VISIBLE : View.GONE);
        boolean isNow = month.equals(YearMonth.now());
        thisMonth.setEnabled(!isNow);
        thisMonth.setAlpha(isNow ? 0.4f : 1f);
    }
}
