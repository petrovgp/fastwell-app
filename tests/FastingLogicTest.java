package app.fastwell;

import java.time.*;
import java.util.*;

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
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
