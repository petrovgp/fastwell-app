package app.fastwell;

import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

/** Plain-JVM tests for the pure logic (run: tests/run.sh). */
public class FastingLogicTest {
    static int failures = 0, checks = 0;
    static void eq(Object exp, Object got, String what) {
        checks++;
        if (!Objects.equals(exp, got)) { failures++; System.out.println("FAIL " + what + ": expected " + exp + " got " + got); }
    }
    static long at(ZoneId z, int y, int m, int d, int h, int min) { return ZonedDateTime.of(y, m, d, h, min, 0, 0, z).toInstant().toEpochMilli(); }

    public static void main(String[] a) {
        ZoneId z = ZoneId.of("Europe/Skopje");
        // plans
        eq(16, FastingLogic.plan("16:8", 0).fastHours, "16:8 hours");
        eq("16:8", FastingLogic.plan("nonsense", 0).id, "unknown plan falls back to 16:8");
        eq(36, FastingLogic.plan("custom", 36).fastHours, "custom 36h");
        eq(0, FastingLogic.plan("custom", 36).eatHours(), "custom 36h eat hours clamp");
        eq(72, FastingLogic.plan("custom", 500).fastHours, "custom clamp max");
        eq(23 * 60L, FastingLogic.plan("23:1", 0).goalMinutes(), "OMAD minutes");
        // stages
        eq(0, FastingLogic.stageIndex(0), "stage 0h");
        eq(1, FastingLogic.stageIndex(4), "stage 4h");
        eq(2, FastingLogic.stageIndex(16.5), "stage 16.5h");
        eq(4, FastingLogic.stageIndex(30), "stage 30h");
        eq(5, FastingLogic.stageIndex(60), "stage 60h");
        // Mifflin-St Jeor: male 30y 175cm 75kg -> BMR 1698.75, x1.375 = 2335.8 -> 2340
        eq(2340, FastingLogic.maintenanceKcal(true, 30, 175, 75, 1), "mifflin male light");
        // female 30y 165cm 60kg -> BMR 1320.25 x1.2 = 1584.3 -> 1580
        eq(1580, FastingLogic.maintenanceKcal(false, 30, 165, 60, 0), "mifflin female sedentary");
        eq(1200, FastingLogic.maintenanceKcal(false, 90, 120, 35, 0), "mifflin floor 1200");
        int[] m = FastingLogic.macroTargets(2340, 75);
        eq(120, m[0], "protein 1.6g/kg");
        eq(73, m[2], "fat 28%");
        eq((int) Math.round((2340 - 120 * 4 - 73 * 9) / 4.0), m[1], "carbs remainder");
        // calorie adjustment and macro split
        eq(2110, FastingLogic.adjustedKcal(2340, -10), "10% deficit rounds to 10");
        eq(2690, FastingLogic.adjustedKcal(2340, 15), "15% surplus");
        eq(2340, FastingLogic.adjustedKcal(2340, 0), "maintenance");
        int[] mg = FastingLogic.macroGrams(2000, new int[]{50, 30, 20});
        eq(250, mg[0], "carbs g");
        eq(150, mg[1], "protein g");
        eq(44, mg[2], "fat g");
        int[] ms = FastingLogic.macroSplit(120, 220, 65);
        eq(100, ms[0] + ms[1] + ms[2], "split sums to 100");
        eq(25, ms[1], "protein share");
        eq(30, ms[2], "fat share");
        eq(100, IntStream.of(FastingLogic.macroSplit(0, 0, 0)).sum(), "empty split sums to 100");
        int[] pct = {60, 25, 20}; // carbs just raised from 55 to 60
        int[] order = {0, 2, 1};
        FastingLogic.rebalance(pct, 0, order);
        eq("[60, 25, 15]", Arrays.toString(pct), "takes from least recent other (fat)");
        eq("[2, 1, 0]", Arrays.toString(order), "changed moves to most recent");
        pct = new int[]{98, 5, 15}; // carbs raised from 80 to 98; fat only has 15 to give
        order = new int[]{2, 1, 0};
        FastingLogic.rebalance(pct, 0, order);
        eq("[98, 2, 0]", Arrays.toString(pct), "spills over to the next column");
        pct = new int[]{50, 25, 15}; // carbs lowered from 60 to 50
        order = new int[]{2, 1, 0};
        FastingLogic.rebalance(pct, 0, order);
        eq("[50, 25, 25]", Arrays.toString(pct), "lowering gives to least recent other");
        // food macro guessing: grams are {carbs, protein, fat}
        double[] gr = {0, 20, 0}; // 300 kcal, protein typed as 20 g
        order = new int[]{0, 2, 1};
        eq(300.0, FastingLogic.balanceGrams(300, gr, 1, order), "macros reach the calories");
        eq("[55.0, 20.0, 0.0]", Arrays.toString(gr), "least recent macro (carbs) takes the rest");
        eq("[0, 2, 1]", Arrays.toString(order), "typed macro moves to most recent");
        gr = new double[]{55, 20, 20}; // fat raised to 20 g
        order = new int[]{0, 2, 1};
        FastingLogic.balanceGrams(300, gr, 2, order);
        eq("[10.0, 20.0, 20.0]", Arrays.toString(gr), "carbs give for the fat");
        gr = new double[]{55, 20, 40}; // fat raised to 40 g = 360 kcal, more than the 300 typed
        order = new int[]{0, 1, 2};
        eq(360.0, FastingLogic.balanceGrams(300, gr, 2, order), "fat alone exceeds the calories");
        eq("[0.0, 0.0, 40.0]", Arrays.toString(gr), "carbs then protein give, typed fat stays");
        gr = new double[]{0, 20, 20};
        order = new int[]{1, 0, 2};
        FastingLogic.balanceGrams(300, gr, 2, order);
        eq("[0.0, 30.0, 20.0]", Arrays.toString(gr), "spills to protein when it is least recent");
        gr = new double[]{55, 20, 0}; // calories lowered to 200 with protein most recent
        order = new int[]{2, 0, 1};
        eq(200.0, FastingLogic.balanceGrams(200, gr, -1, order), "calorie change rebalances");
        eq("[30.0, 20.0, 0.0]", Arrays.toString(gr), "fat at 0 can't give, carbs do");
        eq("[2, 0, 1]", Arrays.toString(order), "calorie change keeps the order");
        eq(236.0, FastingLogic.macroKcal(new double[]{30, 20, 4}), "macro kcal");
        // durations
        eq("16h 05m", FastingLogic.dur(16 * 3600000L + 5 * 60000L), "dur");
        eq("45m", FastingLogic.dur(45 * 60000L), "dur minutes");
        eq("0m", FastingLogic.dur(-5), "dur negative");
        eq("12:40:05", FastingLogic.hms(((12 * 60 + 40) * 60 + 5) * 1000L), "hms");
        eq("100:00:00", FastingLogic.hms(100 * 3600000L), "hms > 99h");
        // meals
        eq(0, FastingLogic.mealForHour(7), "breakfast");
        eq(1, FastingLogic.mealForHour(12), "lunch");
        eq(2, FastingLogic.mealForHour(19), "dinner");
        eq(3, FastingLogic.mealForHour(23), "late snack");
        eq(3, FastingLogic.mealForHour(2), "night snack");
        // streaks
        LocalDate today = LocalDate.of(2026, 10, 28);
        List<long[]> f = new ArrayList<>();
        f.add(new long[]{at(z, 2026, 10, 28, 12, 0), 1});
        f.add(new long[]{at(z, 2026, 10, 27, 12, 0), 1});
        f.add(new long[]{at(z, 2026, 10, 26, 12, 0), 1});
        f.add(new long[]{at(z, 2026, 10, 25, 12, 0), 0}); // missed goal breaks it
        f.add(new long[]{at(z, 2026, 10, 24, 12, 0), 1});
        f.add(new long[]{at(z, 2026, 10, 23, 12, 0), 1});
        f.add(new long[]{at(z, 2026, 10, 22, 12, 0), 1});
        f.add(new long[]{at(z, 2026, 10, 21, 12, 0), 1});
        int[] s = FastingLogic.streaks(f, today, z);
        eq(3, s[0], "current streak");
        eq(4, s[1], "best streak");
        // today not yet done: streak continues from yesterday
        List<long[]> g = new ArrayList<>(f.subList(1, f.size()));
        eq(2, FastingLogic.streaks(g, today, z)[0], "streak alive before today's fast");
        // two days ago last -> broken
        eq(0, FastingLogic.streaks(new ArrayList<>(f.subList(2, f.size())), today, z)[0], "streak broken");
        eq(0, FastingLogic.streaks(new ArrayList<long[]>(), today, z)[1], "empty best");
        // fast ending just after local midnight counts for the new day
        List<long[]> mid = new ArrayList<>();
        mid.add(new long[]{at(z, 2026, 10, 28, 0, 30), 1});
        eq(1, FastingLogic.streaks(mid, today, z)[0], "after-midnight end counts for today");
        // hours per day
        List<LocalDate> days = FastingLogic.lastDays(today, 7);
        eq(7, days.size(), "7 days");
        eq(today, days.get(6), "last day is today");
        List<long[]> se = new ArrayList<>();
        se.add(new long[]{at(z, 2026, 10, 27, 20, 0), at(z, 2026, 10, 28, 12, 0)});
        se.add(new long[]{at(z, 2026, 10, 1, 20, 0), at(z, 2026, 10, 2, 12, 0)}); // outside range
        se.add(new long[]{at(z, 2026, 10, 27, 20, 0), 0}); // active ignored
        double[] h = FastingLogic.fastingHoursPerDay(se, days, z);
        eq(16.0, h[6], "16h attributed to end day");
        eq(0.0, h[5], "nothing on previous day");
        eq(4200.0, FastingLogic.volume(new int[]{10, 8, 8}, new double[]{100, 175, 225}), "volume");
        // water log names
        eq("Small glass", FastingLogic.waterName("150 ml"), "150 ml is a small glass");
        eq("Large glass", FastingLogic.waterName("250 ml"), "250 ml is a large glass");
        eq("Small bottle", FastingLogic.waterName("500 ml"), "500 ml is a small bottle");
        eq("Large bottle", FastingLogic.waterName("1000 ml"), "1000 ml is a large bottle");
        eq("Water container", FastingLogic.waterName("333 ml"), "other volumes are a water container");
        eq("Water container", FastingLogic.waterName("a cup"), "unreadable amounts are a water container");
        eq(250.0, FastingLogic.millilitres(" 250 ml "), "millilitres parsed");
        eq(0.0, FastingLogic.millilitres("100 g"), "grams aren't millilitres");
        // water target dial
        eq(100, FastingLogic.WATER_STEP, "water target moves in 100 ml steps");
        eq(0, (FastingLogic.WATER_MAX - FastingLogic.WATER_MIN) % FastingLogic.WATER_STEP, "water range ends on a step");
        eq(0, (FastingLogic.WATER_DEFAULT - FastingLogic.WATER_MIN) % FastingLogic.WATER_STEP, "water default sits on a step");
        eq(true, FastingLogic.WATER_DEFAULT >= FastingLogic.WATER_MIN && FastingLogic.WATER_DEFAULT <= FastingLogic.WATER_MAX, "water default in range");
        // workout target dial
        eq(true, FastingLogic.WORKOUT_DEFAULT >= FastingLogic.WORKOUT_MIN && FastingLogic.WORKOUT_DEFAULT <= FastingLogic.WORKOUT_MAX, "workout default in range");
        eq(" session", FastingLogic.sessions(1), "one session is singular");
        eq(" sessions", FastingLogic.sessions(0), "no sessions is plural");
        eq(" sessions", FastingLogic.sessions(6), "several sessions are plural");
        // rest days: 2026-11-02 is a Monday
        LocalDate monday = LocalDate.of(2026, 11, 2);
        int weekend = FastingLogic.restBit(DayOfWeek.SATURDAY) | FastingLogic.restBit(DayOfWeek.SUNDAY);
        eq(false, FastingLogic.isRestDay(0, monday), "no rest days tagged");
        eq(false, FastingLogic.isRestDay(weekend, monday), "Monday isn't in the weekend");
        eq(false, FastingLogic.isRestDay(weekend, monday.plusDays(4)), "Friday isn't in the weekend");
        eq(true, FastingLogic.isRestDay(weekend, monday.plusDays(5)), "Saturday is a tagged rest day");
        eq(true, FastingLogic.isRestDay(weekend, monday.plusDays(6)), "Sunday is a tagged rest day");
        eq(false, FastingLogic.isRestDay(weekend, monday.plusDays(7)), "the tag repeats by weekday, not date");
        int everyDay = 0;
        for (DayOfWeek d : DayOfWeek.values()) {
            eq(false, FastingLogic.isRestDay(everyDay, monday.plusDays(d.getValue() - 1)), d + " has its own bit");
            everyDay |= FastingLogic.restBit(d);
        }
        for (int i = 0; i < 7; i++) eq(true, FastingLogic.isRestDay(everyDay, monday.plusDays(i)), "all seven days can rest");
        eq("Sat, Sun", FastingLogic.restDayNames(weekend, DayOfWeek.MONDAY, Locale.ENGLISH), "rest days in week order");
        eq("Sun, Sat", FastingLogic.restDayNames(weekend, DayOfWeek.SUNDAY, Locale.ENGLISH), "week order follows the first day");
        eq("", FastingLogic.restDayNames(0, DayOfWeek.MONDAY, Locale.ENGLISH), "no rest days, no names");
        // food amounts
        eq(150.0, FastingLogic.grams(" 150 g "), "grams parsed");
        eq(150.5, FastingLogic.grams("150.5 g"), "decimal point");
        eq(150.5, FastingLogic.grams("150,5 g"), "decimal comma");
        eq(1234.0, FastingLogic.grams("1,234 g"), "comma thousands");
        eq(1234.0, FastingLogic.grams("1.234 g"), "point thousands");
        eq(1234.5, FastingLogic.grams("1 234,5 g"), "space thousands");
        eq(0.0, FastingLogic.grams(""), "no amount has no grams");
        eq(0.0, FastingLogic.grams("a cup"), "free text has no grams");
        eq(0.0, FastingLogic.grams("250 ml"), "millilitres aren't grams");
        eq(0.0, FastingLogic.grams("2 kg"), "only grams are read");
        eq(0.0, FastingLogic.grams("0 g"), "zero grams is no weight");
        eq(0.0, FastingLogic.grams("1 serving"), "a serving isn't grams");
        eq(1.0, FastingLogic.servings(""), "no amount is one serving");
        eq(1.0, FastingLogic.servings("a cup"), "free text is one serving");
        eq(1.0, FastingLogic.servings("1 serving"), "one serving");
        eq(2.0, FastingLogic.servings("2 servings"), "two servings");
        eq(1.5, FastingLogic.servings("1.5 servings"), "part servings");
        eq(1.0, FastingLogic.servings("0 servings"), "zero servings falls back to one");
        // workout calories
        eq(360L, Math.round(FastingLogic.strengthKcal(3600, 5, 80)), "strength: 1 h, RPE 5, 80 kg");
        eq(216L, Math.round(FastingLogic.strengthKcal(3600, 1, 80)), "strength at RPE 1");
        eq(540L, Math.round(FastingLogic.strengthKcal(3600, 10, 80)), "strength at RPE 10");
        eq(490L, Math.round(FastingLogic.cardioKcal(0, 1800, 5, 100)), "run: 30 min, RPE 5, 100 kg");
        eq(0L, Math.round(FastingLogic.cardioKcal(1, 0, 5, 80)), "no time, no calories");
        eq("Other", FastingLogic.CARDIO[FastingLogic.OTHER_CARDIO], "Other is the last activity");
        for (int i = 0; i < FastingLogic.OTHER_CARDIO; i++) {
            eq(true, FastingLogic.cardioKcal(i, 3600, 5, 80) > 0, "preset has a calorie estimate: " + FastingLogic.CARDIO[i]);
        }
        eq(0.0, FastingLogic.cardioKcal(FastingLogic.OTHER_CARDIO, 3600, 5, 80), "Other has no calorie estimate");
        // workout durations
        eq("30 s", FastingLogic.workoutDur(30), "seconds only");
        eq("45 min", FastingLogic.workoutDur(45 * 60), "whole minutes");
        eq("12 min 30 s", FastingLogic.workoutDur(12 * 60 + 30), "minutes and seconds");
        eq("2 h", FastingLogic.workoutDur(7200), "whole hours");
        eq("1 h 5 min", FastingLogic.workoutDur(3900), "hours and minutes");
        eq("1 h 5 min 9 s", FastingLogic.workoutDur(3909), "hours, minutes and seconds");
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
