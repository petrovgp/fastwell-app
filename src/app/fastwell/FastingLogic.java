package app.fastwell;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
}
