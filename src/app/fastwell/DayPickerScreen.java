package app.fastwell;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Full-screen day chooser for the Food and Workouts journals: a day / month / year dial that can't go past today. */
final class DayPickerScreen extends Screen {
    /** How many years back the year column reaches. */
    static final int YEARS_BACK = 5;
    private static final int DAY = 0, MONTH = 1, YEAR = 2;

    /** What the journal holds for a day, shown under the picked date (e.g. "3 items  -  1240 kcal"). */
    interface Summary { String of(LocalDate d); }

    private final String title;
    private final int accent;
    private final Summary summary;
    private final int firstYear;
    private LocalDate day;

    private Dial dial;
    private TextView dayVal, detail;
    private Button today;

    DayPickerScreen(MainActivity a, String title, int accentRes, Summary summary) {
        super(a);
        this.title = title;
        this.accent = accentRes;
        this.summary = summary;
        day = a.selectedDay;
        firstYear = Math.min(LocalDate.now().getYear() - YEARS_BACK, day.getYear());
    }

    @Override
    boolean showsNav() { return false; }

    @Override
    View build() {
        LinearLayout root = Ui.vbox(c);
        root.setBackgroundColor(Ui.col(c, R.color.bg));

        LinearLayout bar = Ui.hbox(c);
        bar.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 4));
        ImageView back = Ui.iconButton(c, R.drawable.ic_left, R.color.text, "Back");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.handleBack(); }
        });
        bar.addView(back);
        root.addView(bar);

        LinearLayout col = Ui.vbox(c);
        col.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        col.addView(OnboardingScreen.title(c, title));
        col.addView(summaryCard());

        dial = new Dial(c);
        dial.addColumn("DAY", "", 1, day.lengthOfMonth(), 1, 0, day.getDayOfMonth());
        dial.addColumn("MONTH", "", 1, 12, 1, 0, day.getMonthValue());
        dial.setFormat(MONTH, new Dial.Format() {
            @Override
            public String of(double v) { return Month.of((int) v).getDisplayName(TextStyle.SHORT, Locale.getDefault()); }
        });
        dial.addColumn("YEAR", "", firstYear, LocalDate.now().getYear(), 1, 0, day.getYear());
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
        today = Ui.outline(c, "Today", accent);
        today.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { show(LocalDate.now()); }
        });
        bottom.addView(today, Ui.lpm(c, 0, Ui.dp(c, 56), 0, 0, 12, 0));
        ((LinearLayout.LayoutParams) today.getLayoutParams()).weight = 1f;
        Button go = Ui.primary(c, "Show this day", accent);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { a.pickDay(day); }
        });
        bottom.addView(go, Ui.lp(0, Ui.dp(c, 56), 2f));
        root.addView(bottom, Ui.lp(Ui.MATCH, Ui.WRAP));
        update();
        return root;
    }

    private View summaryCard() {
        LinearLayout card = Ui.card(c);
        card.addView(Ui.muted(c, "Selected day"));
        dayVal = Ui.text(c, "", 18, R.color.text, true);
        // Both lines stay one line tall, so long names or summaries can't wrap and push the dial down.
        dayVal.setSingleLine(true);
        dayVal.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(dayVal);
        detail = Ui.text(c, "", 13, accent, true);
        detail.setPadding(0, Ui.dp(c, 8), 0, 0);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(detail);
        return card;
    }

    /** Reads the dial, fitting the day column to the month (leap years included) and turning a future pick into today. */
    private void readDial() {
        int y = (int) dial.value(YEAR), m = (int) dial.value(MONTH);
        int days = LocalDate.of(y, m, 1).lengthOfMonth();
        dial.setMax(DAY, days);
        LocalDate picked = LocalDate.of(y, m, (int) dial.value(DAY));
        LocalDate now = LocalDate.now();
        day = picked.isAfter(now) ? now : picked;
        update();
    }

    private void show(LocalDate d) {
        day = d;
        syncDial();
        update();
    }

    /** Moves any column that disagrees with the picked day onto it; done once the wheel stops so it never jumps mid-drag. */
    private void syncDial() {
        dial.setMax(DAY, day.lengthOfMonth());
        if ((int) dial.value(DAY) != day.getDayOfMonth()) dial.setValue(DAY, day.getDayOfMonth());
        if ((int) dial.value(MONTH) != day.getMonthValue()) dial.setValue(MONTH, day.getMonthValue());
        if ((int) dial.value(YEAR) != day.getYear()) dial.setValue(YEAR, day.getYear());
    }

    private void update() {
        if (dayVal == null) return;
        dayVal.setText(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())));
        detail.setText(summary != null ? summary.of(day) : "");
        detail.setVisibility(summary != null ? View.VISIBLE : View.GONE);
        boolean isToday = day.equals(LocalDate.now());
        today.setEnabled(!isToday);
        today.setAlpha(isToday ? 0.4f : 1f);
    }
}
