package app.fastwell;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Pure logic with no Android dependencies, so it can be unit-tested on the JVM. */
final class FastingLogic {
    private FastingLogic() {}

    static final class Plan {
        final String id;
        final String name;
        final int fastHours;

        Plan(String id, String name, int fastHours) {
            this.id = id;
            this.name = name;
            this.fastHours = fastHours;
        }

        int eatHours() { return Math.max(0, 24 - fastHours); }

        long goalMinutes() { return fastHours * 60L; }

        String ratio() { return fastHours + ":" + eatHours(); }
    }

    static final Plan[] PRESETS = {
        new Plan("13:11", "13:11  Circadian", 13),
        new Plan("16:8", "16:8  Leangains", 16),
        new Plan("18:6", "18:6", 18),
        new Plan("20:4", "20:4  Warrior", 20),
        new Plan("23:1", "23:1  One meal a day", 23),
    };

    static final String CUSTOM = "custom";

    static Plan plan(String id, int customHours) {
        if (CUSTOM.equals(id)) {
            int h = Math.max(1, Math.min(72, customHours));
            return new Plan(CUSTOM, "Custom  " + h + ":" + Math.max(0, 24 - h), h);
        }
        for (Plan p : PRESETS) if (p.id.equals(id)) return p;
        return PRESETS[1];
    }

    /** General-information stages; not medical claims. */
    static final int[] STAGE_HOURS = {0, 4, 12, 18, 24, 48};
    static final String[] STAGE_NAMES = {
        "Digesting your last meal",
        "Blood sugar settling",
        "Fat-burning zone",
        "Ketosis ramping up",
        "Deep ketosis",
        "Extended fast",
    };

    static int stageIndex(double hours) {
        int idx = 0;
        for (int i = 0; i < STAGE_HOURS.length; i++) if (hours >= STAGE_HOURS[i]) idx = i;
        return idx;
    }

    static final String[] ACTIVITY = {
        "Sedentary (desk job, little exercise)",
        "Light (1-3 workouts a week)",
        "Moderate (3-5 workouts a week)",
        "Active (6-7 workouts a week)",
        "Very active (physical job + training)",
    };
    static final double[] ACTIVITY_FACTOR = {1.2, 1.375, 1.55, 1.725, 1.9};

    /** Mifflin-St Jeor maintenance estimate, rounded to 10 kcal. */
    static int maintenanceKcal(boolean male, int age, double heightCm, double weightKg, int activity) {
        double bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + (male ? 5 : -161);
        int a = Math.max(0, Math.min(ACTIVITY_FACTOR.length - 1, activity));
        long kcal = Math.round(bmr * ACTIVITY_FACTOR[a] / 10.0) * 10;
        return (int) Math.max(1200, kcal);
    }

    /** {protein g, carbs g, fat g} for a calorie target. */
    static int[] macroTargets(int kcal, double weightKg) {
        int protein = (int) Math.round(1.6 * weightKg);
        int fat = (int) Math.round(kcal * 0.28 / 9.0);
        int carbs = (int) Math.max(0, Math.round((kcal - protein * 4 - fat * 9) / 4.0));
        return new int[] {protein, carbs, fat};
    }

    /** Column order of a macro percent split. */
    static final int CARBS = 0, PROTEIN = 1, FAT = 2;

    /** Maintenance calories with a percent surplus (+) or deficit (-), rounded to 10 kcal. */
    static int adjustedKcal(int maintenance, int percent) {
        return (int) Math.round(maintenance * (100 + percent) / 1000.0) * 10;
    }

    /** {carbs g, protein g, fat g} for a calorie target split by {carbs, protein, fat} percent. */
    static int[] macroGrams(int kcal, int[] pct) {
        return new int[] {
            (int) Math.round(kcal * pct[CARBS] / 400.0),
            (int) Math.round(kcal * pct[PROTEIN] / 400.0),
            (int) Math.round(kcal * pct[FAT] / 900.0),
        };
    }

    /** {carbs, protein, fat} percent of calories for gram targets, summing to exactly 100. */
    static int[] macroSplit(int proteinG, int carbsG, int fatG) {
        double total = proteinG * 4.0 + carbsG * 4.0 + fatG * 9.0;
        if (total <= 0) return new int[] {50, 25, 25};
        int p = (int) Math.round(proteinG * 400.0 / total);
        int f = Math.min(100 - p, (int) Math.round(fatG * 900.0 / total));
        return new int[] {100 - p - f, p, f};
    }

    /**
     * After pct[changed] was set, takes the difference from the other columns so the split sums to 100
     * again: least recently changed column first ({@code order} lists columns least recent first, and
     * {@code changed} is moved to its end).
     */
    static void rebalance(int[] pct, int changed, int[] order) {
        int k = 0;
        for (int col : order) if (col != changed) order[k++] = col;
        order[k] = changed;
        int diff = pct[0] + pct[1] + pct[2] - 100;
        for (int i = 0; i < order.length - 1 && diff != 0; i++) {
            int col = order[i];
            int v = Math.max(0, Math.min(100, pct[col] - diff));
            diff -= pct[col] - v;
            pct[col] = v;
        }
    }

    /** Calories per gram of {carbs, protein, fat}. */
    static final double[] KCAL_PER_G = {4, 4, 9};

    /** Calories of {carbs, protein, fat} grams. */
    static double macroKcal(double[] g) {
        return g[CARBS] * KCAL_PER_G[CARBS] + g[PROTEIN] * KCAL_PER_G[PROTEIN] + g[FAT] * KCAL_PER_G[FAT];
    }

    /**
     * Like {@link #rebalance} for a food's {carbs, protein, fat} grams: after g[changed] was typed, takes the
     * calorie difference from the other macros so they add up to kcal again, least recently changed first.
     * With changed = -1 (the calories were typed) every macro can absorb it. Returns the calories the macros
     * add up to afterwards, which is more than kcal when the changed macro alone exceeds it.
     */
    static double balanceGrams(double kcal, double[] g, int changed, int[] order) {
        int adjustable = order.length;
        if (changed >= 0) {
            int k = 0;
            for (int col : order) if (col != changed) order[k++] = col;
            order[k] = changed;
            adjustable--;
        }
        double diff = macroKcal(g) - kcal;
        for (int i = 0; i < adjustable && Math.abs(diff) > 1e-9; i++) {
            int col = order[i];
            double v = Math.max(0, g[col] - diff / KCAL_PER_G[col]);
            diff -= (g[col] - v) * KCAL_PER_G[col];
            g[col] = v;
        }
        return kcal + diff;
    }

    /**
     * Streaks of consecutive days that contain at least one completed fast that met its goal,
     * attributed to the local day it ended. The current streak stays alive through today
     * if yesterday was met. Returns {current, best}.
     */
    static int[] streaks(List<long[]> endAtAndMet, LocalDate today, ZoneId zone) {
        TreeSet<LocalDate> days = new TreeSet<LocalDate>();
        for (long[] f : endAtAndMet) {
            if (f[1] != 0) days.add(Instant.ofEpochMilli(f[0]).atZone(zone).toLocalDate());
        }
        int current = 0;
        LocalDate d = days.contains(today) ? today : today.minusDays(1);
        while (days.contains(d)) {
            current++;
            d = d.minusDays(1);
        }
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate day : days) {
            run = (prev != null && prev.plusDays(1).equals(day)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = day;
        }
        return new int[] {current, Math.max(best, current)};
    }

    /** "16h 05m", "45m". */
    static String dur(long ms) {
        if (ms < 0) ms = 0;
        long totalMin = ms / 60000L;
        long h = totalMin / 60;
        long m = totalMin % 60;
        if (h == 0) return m + "m";
        return String.format(Locale.US, "%dh %02dm", h, m);
    }

    /** "12:40:05". */
    static String hms(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000L;
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    /** What the Fast screen and the home-screen widget show around the clock. */
    static final class Status {
        final float progress;
        final String label, remaining, stage, next;

        Status(float progress, String label, String remaining, String stage, String next) {
            this.progress = progress;
            this.label = label;
            this.remaining = remaining;
            this.stage = stage;
            this.next = next;
        }
    }

    static Status fasting(long elapsedMs, long goalMs) {
        float p = elapsedMs / (float) goalMs;
        int idx = stageIndex(elapsedMs / 3_600_000.0);
        String next = "Fasts this long should be medically supervised.";
        if (idx + 1 < STAGE_HOURS.length) {
            long nextAt = STAGE_HOURS[idx + 1] * 3_600_000L;
            next = "Next: " + STAGE_NAMES[idx + 1] + " in " + dur(nextAt - elapsedMs);
        }
        return new Status(p,
                p >= 1 ? "Goal reached" : "Elapsed  -  " + Math.min(100, (int) (p * 100)) + "%",
                p >= 1 ? "+" + dur(elapsedMs - goalMs) + " over goal" : dur(goalMs - elapsedMs) + " to go",
                STAGE_NAMES[idx], next);
    }

    static Status notFasting(int goalHours) {
        return new Status(0, "Not fasting", "Goal " + goalHours + " hours", "Eating window",
                "Start a fast after your last meal of the day.");
    }

    /** The next whole minute of a fast, when its minute-based text next changes. */
    static long nextMinute(long now, long startAt) {
        return now + 60_000L - (now - startAt) % 60_000L;
    }

    /** Whether a widget tile has room for the stage text beside the ring (widget_fast_wide.xml), not below it. */
    static boolean widgetWide(int widthDp, int heightDp) { return heightDp > 0 && widthDp * 4 >= heightDp * 7; }

    /** Ring size that fills a widget tile of the given size; launchers may report 0 for a size they don't know. */
    static int widgetRingDp(int widthDp, int heightDp) {
        if (widthDp <= 0 || heightDp <= 0) return 108;
        // Padding in the widget layouts: 4dp above and below the wide ring, 8dp beside the stacked one.
        if (widgetWide(widthDp, heightDp)) return Math.max(90, heightDp - 8);
        return Math.max(90, Math.min(widthDp - 16, Math.round(heightDp * 0.64f)));
    }

    /** The widget ring's stroke, as a share of its size. */
    static final float WIDGET_STROKE = 0.065f;

    /** Space inside the widget ring: its size less the stroke and 7dp of fixed margin on each side. */
    private static float widgetInnerDp(int ringDp) { return ringDp - 14 - 2 * ringDp * WIDGET_STROKE; }

    static int widgetClockDp(int ringDp) { return Math.round(widgetInnerDp(ringDp) * 0.2f); }

    /** The lines above and below the widget's clock. */
    static int widgetLineDp(int ringDp) { return Math.round(widgetInnerDp(ringDp) * 0.1f); }

    /** Meal slot from local hour: 0 breakfast, 1 lunch, 2 dinner, 3 snacks. */
    static int mealForHour(int hour) {
        if (hour >= 4 && hour < 11) return 0;
        if (hour >= 11 && hour < 16) return 1;
        if (hour >= 16 && hour < 21) return 2;
        return 3;
    }

    static final String[] MEALS = {"Breakfast", "Lunch", "Dinner", "Snacks", "Water"};
    /** Index in MEALS of the water category; the slots before it are food meals. */
    static final int WATER = 4;
    /** What a water log is called, by preset; the last one is the custom amount. */
    static final String[] WATER_PRESETS = {"Small glass", "Large glass", "Small bottle", "Large bottle", "Water container"};
    static final int[] WATER_ML = {150, 250, 500, 1000, 0};
    static final int CUSTOM_WATER = 4;
    /** Daily water target in ml, as set on its dial. */
    static final int WATER_MIN = 500, WATER_MAX = 6000, WATER_STEP = 100, WATER_DEFAULT = 2500;
    /** Daily workout target in sessions, as set on its dial. */
    static final int WORKOUT_MIN = 1, WORKOUT_MAX = 6, WORKOUT_DEFAULT = 1;
    /** Activities on the cardio dial; Other, the last one, is named by the user and has no calorie estimate. */
    static final String[] CARDIO = {"Run", "Walk", "Cycle", "Swim", "Row", "Elliptical", "Hike", "Other"};
    static final int OTHER_CARDIO = 7;
    /** Metabolic equivalents at RPE 5, by cardio preset. */
    private static final double[] CARDIO_MET = {9.8, 3.5, 7.5, 7.0, 7.0, 5.0, 6.0};
    /** A generic gym session, rests between sets included. */
    private static final double STRENGTH_MET = 4.5;
    static final int RPE_MIN = 1, RPE_MAX = 10, RPE_DEFAULT = 5;

    /** MET x kg x hours, with the MET scaled from 0.6x at RPE 1 through 1x at RPE 5 to 1.5x at RPE 10. */
    private static double workoutKcal(double met, int seconds, int rpe, double weightKg) {
        return met * (0.5 + 0.1 * rpe) * weightKg * seconds / 3600.0;
    }

    static double cardioKcal(int activity, int seconds, int rpe, double weightKg) {
        if (activity == OTHER_CARDIO) return 0;
        return workoutKcal(CARDIO_MET[activity], seconds, rpe, weightKg);
    }

    static double strengthKcal(int seconds, int rpe, double weightKg) {
        return workoutKcal(STRENGTH_MET, seconds, rpe, weightKg);
    }

    /** A daily calorie or macro target, raised by the share of the calorie target that workouts burned. */
    static int raisedTarget(int target, int kcalTarget, double burned) {
        if (kcalTarget <= 0) return target;
        return (int) Math.round(target * (1 + burned / kcalTarget));
    }

    /** A workout's length, leaving out any part that is zero: "1 h 5 min", "12 min 30 s". */
    static String workoutDur(int seconds) {
        int h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append(" h ");
        if (m > 0) sb.append(m).append(" min ");
        if (s > 0 || sb.length() == 0) sb.append(s).append(" s ");
        return sb.toString().trim();
    }

    /** The unit after a count of n workouts. */
    static String sessions(int n) { return n == 1 ? " session" : " sessions"; }

    /** A weekday's bit in the rest days mask. */
    static int restBit(DayOfWeek d) { return 1 << (d.getValue() - 1); }

    static boolean isRestDay(int restDays, LocalDate day) { return (restDays & restBit(day.getDayOfWeek())) != 0; }

    /** Short names of the rest days like "Sat, Sun", in week order from first. */
    static String restDayNames(int restDays, DayOfWeek first, Locale locale) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            DayOfWeek d = first.plus(i);
            if ((restDays & restBit(d)) == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(d.getDisplayName(TextStyle.SHORT, locale));
        }
        return sb.toString();
    }

    /** Volume of an amount like "250 ml", or 0 when it isn't in millilitres. */
    static double millilitres(String amount) {
        String s = amount.trim();
        if (!s.endsWith("ml")) return 0;
        try {
            return Double.parseDouble(s.substring(0, s.length() - 2).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Weight of an amount like "150 g", or 0 when it isn't in grams. Older logs were saved in the phone's
     * number format with at most one decimal, so a separator before three digits is always a thousands one.
     */
    static double grams(String amount) {
        String s = amount.trim();
        if (!s.endsWith("g")) return 0;
        s = s.substring(0, s.length() - 1).replaceAll("[\\s\\u00a0\\u202f]", "")
                .replaceAll("[.,](?=\\d{3}(\\D|$))", "").replace(',', '.');
        try {
            return Math.max(0, Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Count in an amount like "2 servings"; a log with no weight or readable amount is 1 serving. */
    static double servings(String amount) {
        String s = amount.trim();
        s = s.endsWith("servings") ? s.substring(0, s.length() - 8) : s.endsWith("serving") ? s.substring(0, s.length() - 7) : "";
        try {
            double n = Double.parseDouble(s.trim());
            return n > 0 ? n : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Name for a water log that didn't record its preset: the preset of that volume, else custom. */
    static String waterName(String amount) {
        double ml = millilitres(amount);
        for (int i = 0; i < CUSTOM_WATER; i++) if (ml == WATER_ML[i]) return WATER_PRESETS[i];
        return WATER_PRESETS[CUSTOM_WATER];
    }

    /** Hours of fasting attributed to each of the given days (by end day). */
    static double[] fastingHoursPerDay(List<long[]> startEnd, List<LocalDate> days, ZoneId zone) {
        double[] out = new double[days.size()];
        for (long[] f : startEnd) {
            if (f[1] <= 0) continue;
            LocalDate end = Instant.ofEpochMilli(f[1]).atZone(zone).toLocalDate();
            int idx = days.indexOf(end);
            if (idx >= 0) out[idx] += (f[1] - f[0]) / 3_600_000.0;
        }
        return out;
    }

    static List<LocalDate> lastDays(LocalDate today, int n) {
        List<LocalDate> out = new ArrayList<LocalDate>();
        for (int i = n - 1; i >= 0; i--) out.add(today.minusDays(i));
        return Collections.unmodifiableList(out);
    }

    /** Training volume: sum of reps * weight. */
    static double volume(int[] reps, double[] weights) {
        double v = 0;
        for (int i = 0; i < reps.length; i++) v += reps[i] * weights[i];
        return v;
    }

    // ---------- body weight ----------

    static double tenth(double v) { return Math.round(v * 10) / 10.0; }

    /** The whole kilos and the decimal of a weight, for its two dials; they add up to the weight to the tenth. */
    static int wholeKg(double kg) { return (int) (Math.round(kg * 10) / 10); }
    static double decimalKg(double kg) { return Math.round(kg * 10) % 10 / 10.0; }
    static double joinKg(int whole, double decimal) { return tenth(whole + decimal); }

    static String kgText(double kg) { return String.format(Locale.getDefault(), "%.1f kg", kg); }

    /** "down 0.8 kg", "up 0.5 kg" or "no change", to the tenth. */
    static String weightChange(double from, double to) {
        double d = tenth(to - from);
        if (d == 0) return "no change";
        return (d < 0 ? "down " : "up ") + kgText(Math.abs(d));
    }

    /** The latest weight and the change from the first log in perDay, where 0 is a day without a log. */
    static String weightSummary(double[] perDay, String span) {
        double first = 0, last = 0;
        int n = 0;
        for (double kg : perDay) {
            if (kg <= 0) continue;
            if (n++ == 0) first = kg;
            last = kg;
        }
        if (n == 0) return "No data yet";
        return n == 1 ? kgText(last) : kgText(last) + "  -  " + weightChange(first, last) + " " + span;
    }
}
