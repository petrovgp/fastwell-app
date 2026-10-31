package app.fastwell;

import android.content.Context;

/** Wheel for a body weight: one column of whole kilos in 1 kg steps and one of decimals in 0.1 kg steps. */
final class WeightDial {
    private static final int MIN = 35, MAX = 250;

    final Dial view;
    private final int whole, decimal;

    WeightDial(Context c, double kg) {
        view = new Dial(c);
        whole = view.addColumn("WHOLE KG", "", MIN, MAX, 1, 0, FastingLogic.wholeKg(kg));
        decimal = view.addColumn("DECIMAL KG", "", 0, 0.9, 0.1, 1, FastingLogic.decimalKg(kg));
        view.setFormat(decimal, new Dial.Format() {
            @Override
            public String of(double v) { return "." + Math.round(v * 10); }
        });
    }

    double kg() { return FastingLogic.joinKg((int) Math.round(view.value(whole)), view.value(decimal)); }

    void onChange(final Runnable r) {
        view.setListener(new Dial.Listener() {
            @Override
            public void onChange(int column, double value) { r.run(); }
        });
    }
}
