package app.fastwell;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All journals in one SQLite database. Every timestamp is epoch milliseconds (UTC). */
final class Db extends SQLiteOpenHelper {

    // ---------- models ----------

    static final class Fast {
        long id, startAt, endAt, goalMin;
        String plan = "", note = "";
        int mood;

        boolean active() { return endAt == 0; }
        long durationMs(long now) { return (active() ? now : endAt) - startAt; }
        long goalMs() { return goalMin * 60000L; }
        boolean met() { return !active() && endAt - startAt >= goalMs(); }
    }

    static final class Food {
        long id, loggedAt;
        String day = "", name = "", amount = "";
        int meal;
        double kcal, protein, carbs, fat;
        boolean zeroCal;

        Food copy() {
            Food f = new Food();
            f.loggedAt = loggedAt; f.day = day; f.name = name; f.amount = amount; f.meal = meal;
            f.kcal = kcal; f.protein = protein; f.carbs = carbs; f.fat = fat; f.zeroCal = zeroCal;
            return f;
        }
    }

    static final class Item {
        long id;
        String name = "", brand = "", barcode = "";
        double kcal100, p100, c100, f100, servingG;
    }

    static final class Exercise {
        long id;
        String name, muscle;
    }

    static final int STRENGTH = 0, CARDIO = 1;

    static final class Workout {
        long id, startAt, endAt;
        String day = "", name = "", note = "";
        /** rpe is 0 on workouts logged before it could be set. */
        int type, durationSec, rpe;
        boolean fasted;
        double distanceKm, kcal;
    }

    static final class WSet {
        long id, workoutId, exerciseId;
        int ord, reps;
        double weight;
        boolean done;
        String exerciseName = "";
    }

    // ---------- setup ----------

    private static Db inst;

    static synchronized Db get(Context c) {
        if (inst == null) inst = new Db(c.getApplicationContext());
        return inst;
    }

    private Db(Context c) { super(c, "fastwell.db", null, 2); }

    static final String[] TABLES = {"fast", "food_entry", "food_item", "exercise", "workout", "workout_set"};

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE fast (id INTEGER PRIMARY KEY AUTOINCREMENT, start_at INTEGER NOT NULL, "
                + "end_at INTEGER NOT NULL DEFAULT 0, goal_min INTEGER NOT NULL, plan TEXT, mood INTEGER DEFAULT 0, note TEXT)");
        d.execSQL("CREATE TABLE food_entry (id INTEGER PRIMARY KEY AUTOINCREMENT, logged_at INTEGER NOT NULL, "
                + "day TEXT NOT NULL, meal INTEGER NOT NULL, name TEXT NOT NULL, amount TEXT, kcal REAL NOT NULL DEFAULT 0, "
                + "protein REAL DEFAULT 0, carbs REAL DEFAULT 0, fat REAL DEFAULT 0, zero_cal INTEGER DEFAULT 0)");
        d.execSQL("CREATE INDEX idx_food_day ON food_entry(day)");
        d.execSQL("CREATE TABLE food_item (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, brand TEXT, "
                + "barcode TEXT, kcal100 REAL, p100 REAL, c100 REAL, f100 REAL, serving_g REAL, last_used INTEGER)");
        d.execSQL("CREATE TABLE exercise (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, muscle TEXT, custom INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE workout (id INTEGER PRIMARY KEY AUTOINCREMENT, start_at INTEGER NOT NULL, end_at INTEGER DEFAULT 0, "
                + "day TEXT NOT NULL, type INTEGER NOT NULL, name TEXT, fasted INTEGER DEFAULT 0, note TEXT, "
                + "duration_min INTEGER DEFAULT 0, distance_km REAL DEFAULT 0, kcal REAL DEFAULT 0, "
                + "duration_sec INTEGER DEFAULT 0, rpe INTEGER DEFAULT 0)");
        d.execSQL("CREATE INDEX idx_workout_day ON workout(day)");
        d.execSQL("CREATE TABLE workout_set (id INTEGER PRIMARY KEY AUTOINCREMENT, workout_id INTEGER NOT NULL, "
                + "exercise_id INTEGER NOT NULL, ord INTEGER NOT NULL, reps INTEGER DEFAULT 0, weight REAL DEFAULT 0, done INTEGER DEFAULT 0)");
        d.execSQL("CREATE INDEX idx_set_workout ON workout_set(workout_id)");
        seedExercises(d);
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {
        if (oldV < 2) {
            d.execSQL("ALTER TABLE workout ADD COLUMN duration_sec INTEGER DEFAULT 0");
            d.execSQL("ALTER TABLE workout ADD COLUMN rpe INTEGER DEFAULT 0");
            fillDurations(d);
        }
    }

    /** Gives v1 workouts, from an upgrade or an old backup, a duration; a live strength workout ends after an hour. */
    private static void fillDurations(SQLiteDatabase d) {
        d.execSQL("UPDATE workout SET end_at = start_at + 3600000 WHERE type = " + STRENGTH + " AND end_at = 0");
        d.execSQL("UPDATE workout SET duration_sec = CASE type WHEN " + STRENGTH
                + " THEN (end_at - start_at) / 1000 ELSE duration_min * 60 END WHERE duration_sec = 0");
    }

    private static final String[][] SEED = {
        {"Bench press", "Chest"}, {"Incline bench press", "Chest"}, {"Dumbbell bench press", "Chest"},
        {"Incline dumbbell press", "Chest"}, {"Chest fly", "Chest"}, {"Push-up", "Chest"}, {"Dips", "Chest"},
        {"Cable crossover", "Chest"},
        {"Deadlift", "Back"}, {"Pull-up", "Back"}, {"Chin-up", "Back"}, {"Lat pulldown", "Back"},
        {"Barbell row", "Back"}, {"Dumbbell row", "Back"}, {"Seated cable row", "Back"}, {"T-bar row", "Back"},
        {"Face pull", "Back"}, {"Back extension", "Back"},
        {"Overhead press", "Shoulders"}, {"Dumbbell shoulder press", "Shoulders"}, {"Lateral raise", "Shoulders"},
        {"Rear delt fly", "Shoulders"}, {"Front raise", "Shoulders"}, {"Upright row", "Shoulders"}, {"Shrug", "Shoulders"},
        {"Barbell curl", "Arms"}, {"Dumbbell curl", "Arms"}, {"Hammer curl", "Arms"}, {"Preacher curl", "Arms"},
        {"Triceps pushdown", "Arms"}, {"Skull crusher", "Arms"}, {"Overhead triceps extension", "Arms"},
        {"Close-grip bench press", "Arms"},
        {"Back squat", "Legs"}, {"Front squat", "Legs"}, {"Leg press", "Legs"}, {"Romanian deadlift", "Legs"},
        {"Bulgarian split squat", "Legs"}, {"Lunge", "Legs"}, {"Leg extension", "Legs"}, {"Leg curl", "Legs"},
        {"Hip thrust", "Legs"}, {"Calf raise", "Legs"}, {"Goblet squat", "Legs"}, {"Hack squat", "Legs"},
        {"Plank", "Core"}, {"Hanging leg raise", "Core"}, {"Cable crunch", "Core"}, {"Ab wheel rollout", "Core"},
        {"Russian twist", "Core"}, {"Sit-up", "Core"},
        {"Kettlebell swing", "Full body"}, {"Clean and press", "Full body"}, {"Burpee", "Full body"},
        {"Farmer's carry", "Full body"}, {"Thruster", "Full body"},
    };

    private static void seedExercises(SQLiteDatabase d) {
        for (String[] e : SEED) {
            ContentValues v = new ContentValues();
            v.put("name", e[0]);
            v.put("muscle", e[1]);
            d.insert("exercise", null, v);
        }
    }

    static String dayOf(long t) {
        return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private static long l(Cursor c, String col) { return c.getLong(c.getColumnIndexOrThrow(col)); }
    private static int i(Cursor c, String col) { return c.getInt(c.getColumnIndexOrThrow(col)); }
    private static double dbl(Cursor c, String col) { return c.getDouble(c.getColumnIndexOrThrow(col)); }
    private static String s(Cursor c, String col) {
        String v = c.getString(c.getColumnIndexOrThrow(col));
        return v == null ? "" : v;
    }

    // ---------- fasts ----------

    private static Fast fast(Cursor c) {
        Fast f = new Fast();
        f.id = l(c, "id"); f.startAt = l(c, "start_at"); f.endAt = l(c, "end_at");
        f.goalMin = l(c, "goal_min"); f.plan = s(c, "plan"); f.mood = i(c, "mood"); f.note = s(c, "note");
        return f;
    }

    private List<Fast> fasts(String where, String[] args, String limit) {
        List<Fast> out = new ArrayList<Fast>();
        Cursor c = getReadableDatabase().query("fast", null, where, args, null, null, "start_at DESC", limit);
        try { while (c.moveToNext()) out.add(fast(c)); } finally { c.close(); }
        return out;
    }

    Fast activeFast() {
        List<Fast> l = fasts("end_at = 0", null, "1");
        return l.isEmpty() ? null : l.get(0);
    }

    Fast lastCompletedFast() {
        List<Fast> l = fasts("end_at > 0", null, "1");
        return l.isEmpty() ? null : l.get(0);
    }

    Fast getFast(long id) {
        List<Fast> l = fasts("id = ?", new String[] {String.valueOf(id)}, "1");
        return l.isEmpty() ? null : l.get(0);
    }

    List<Fast> completedFasts(int limit) { return fasts("end_at > 0", null, String.valueOf(limit)); }

    List<Fast> completedFastsSince(long since) {
        return fasts("end_at > 0 AND end_at >= ?", new String[] {String.valueOf(since)}, null);
    }

    /** Starts a fast unless one is already running; returns the active fast's id. */
    long startFast(long startAt, long goalMin, String plan) {
        Fast a = activeFast();
        if (a != null) return a.id;
        ContentValues v = new ContentValues();
        v.put("start_at", startAt);
        v.put("goal_min", goalMin);
        v.put("plan", plan);
        return getWritableDatabase().insert("fast", null, v);
    }

    void endFast(long id, long endAt, int mood, String note) {
        ContentValues v = new ContentValues();
        v.put("end_at", endAt);
        v.put("mood", mood);
        v.put("note", note);
        getWritableDatabase().update("fast", v, "id = ?", new String[] {String.valueOf(id)});
    }

    void updateFast(Fast f) {
        ContentValues v = new ContentValues();
        v.put("start_at", f.startAt);
        v.put("end_at", f.endAt);
        v.put("goal_min", f.goalMin);
        v.put("plan", f.plan);
        v.put("mood", f.mood);
        v.put("note", f.note);
        getWritableDatabase().update("fast", v, "id = ?", new String[] {String.valueOf(f.id)});
    }

    void insertFast(Fast f) {
        ContentValues v = new ContentValues();
        v.put("start_at", f.startAt);
        v.put("end_at", f.endAt);
        v.put("goal_min", f.goalMin);
        v.put("plan", f.plan);
        v.put("mood", f.mood);
        v.put("note", f.note);
        f.id = getWritableDatabase().insert("fast", null, v);
    }

    void deleteFast(long id) {
        getWritableDatabase().delete("fast", "id = ?", new String[] {String.valueOf(id)});
    }

    // ---------- food ----------

    private static Food food(Cursor c) {
        Food f = new Food();
        f.id = l(c, "id"); f.loggedAt = l(c, "logged_at"); f.day = s(c, "day"); f.meal = i(c, "meal");
        f.name = s(c, "name"); f.amount = s(c, "amount"); f.kcal = dbl(c, "kcal");
        f.protein = dbl(c, "protein"); f.carbs = dbl(c, "carbs"); f.fat = dbl(c, "fat");
        f.zeroCal = i(c, "zero_cal") != 0;
        // Water used to be logged without its preset, so older entries and backups all say "Water".
        if (f.meal == FastingLogic.WATER && f.name.equals("Water")) f.name = FastingLogic.waterName(f.amount);
        return f;
    }

    private static ContentValues values(Food f) {
        ContentValues v = new ContentValues();
        v.put("logged_at", f.loggedAt);
        v.put("day", f.day.isEmpty() ? dayOf(f.loggedAt) : f.day);
        v.put("meal", f.meal);
        v.put("name", f.name);
        v.put("amount", f.amount);
        v.put("kcal", f.kcal);
        v.put("protein", f.protein);
        v.put("carbs", f.carbs);
        v.put("fat", f.fat);
        v.put("zero_cal", f.zeroCal ? 1 : 0);
        return v;
    }

    long addFood(Food f) {
        f.id = getWritableDatabase().insert("food_entry", null, values(f));
        return f.id;
    }

    void updateFood(Food f) {
        getWritableDatabase().update("food_entry", values(f), "id = ?", new String[] {String.valueOf(f.id)});
    }

    void deleteFood(long id) {
        getWritableDatabase().delete("food_entry", "id = ?", new String[] {String.valueOf(id)});
    }

    List<Food> foodForDay(String day) {
        List<Food> out = new ArrayList<Food>();
        Cursor c = getReadableDatabase().query("food_entry", null, "day = ?", new String[] {day},
                null, null, "meal, logged_at");
        try { while (c.moveToNext()) out.add(food(c)); } finally { c.close(); }
        return out;
    }

    /** {kcal, protein, carbs, fat} for a day. */
    double[] totalsForDay(String day) {
        Cursor c = getReadableDatabase().rawQuery("SELECT TOTAL(kcal), TOTAL(protein), TOTAL(carbs), TOTAL(fat) "
                + "FROM food_entry WHERE day = ?", new String[] {day});
        try {
            if (c.moveToFirst()) return new double[] {c.getDouble(0), c.getDouble(1), c.getDouble(2), c.getDouble(3)};
        } finally { c.close(); }
        return new double[4];
    }

    Map<String, Double> kcalByDay(String fromDay, String toDay) {
        Map<String, Double> out = new LinkedHashMap<String, Double>();
        Cursor c = getReadableDatabase().rawQuery("SELECT day, TOTAL(kcal) FROM food_entry WHERE day BETWEEN ? AND ? "
                + "GROUP BY day", new String[] {fromDay, toDay});
        try { while (c.moveToNext()) out.put(c.getString(0), c.getDouble(1)); } finally { c.close(); }
        return out;
    }

    /** Most recent distinct foods (by name), for one-tap re-logging. */
    List<Food> recentFoods(int limit) {
        List<Food> out = new ArrayList<Food>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM food_entry WHERE id IN "
                + "(SELECT MAX(id) FROM food_entry GROUP BY lower(name)) ORDER BY logged_at DESC LIMIT " + limit, null);
        try { while (c.moveToNext()) out.add(food(c)); } finally { c.close(); }
        return out;
    }

    // ---------- food items (Open Food Facts cache) ----------

    private static Item item(Cursor c) {
        Item it = new Item();
        it.id = l(c, "id"); it.name = s(c, "name"); it.brand = s(c, "brand"); it.barcode = s(c, "barcode");
        it.kcal100 = dbl(c, "kcal100"); it.p100 = dbl(c, "p100"); it.c100 = dbl(c, "c100"); it.f100 = dbl(c, "f100");
        it.servingG = dbl(c, "serving_g");
        return it;
    }

    void saveItem(Item it) {
        ContentValues v = new ContentValues();
        v.put("name", it.name);
        v.put("brand", it.brand);
        v.put("barcode", it.barcode);
        v.put("kcal100", it.kcal100);
        v.put("p100", it.p100);
        v.put("c100", it.c100);
        v.put("f100", it.f100);
        v.put("serving_g", it.servingG);
        v.put("last_used", System.currentTimeMillis());
        SQLiteDatabase d = getWritableDatabase();
        int n = 0;
        if (!it.barcode.isEmpty()) n = d.update("food_item", v, "barcode = ?", new String[] {it.barcode});
        else n = d.update("food_item", v, "barcode = '' AND lower(name) = lower(?) AND lower(brand) = lower(?)",
                new String[] {it.name, it.brand});
        if (n == 0) it.id = d.insert("food_item", null, v);
    }

    Item itemByBarcode(String code) {
        Cursor c = getReadableDatabase().query("food_item", null, "barcode = ?", new String[] {code}, null, null, null, "1");
        try { return c.moveToFirst() ? item(c) : null; } finally { c.close(); }
    }

    List<Item> searchItems(String q, int limit) {
        List<Item> out = new ArrayList<Item>();
        String like = "%" + q.trim() + "%";
        Cursor c = getReadableDatabase().query("food_item", null, "name LIKE ? OR brand LIKE ?",
                new String[] {like, like}, null, null, "last_used DESC", String.valueOf(limit));
        try { while (c.moveToNext()) out.add(item(c)); } finally { c.close(); }
        return out;
    }

    // ---------- exercises ----------

    List<Exercise> exercises(String q) {
        List<Exercise> out = new ArrayList<Exercise>();
        // Recently used first, then alphabetical.
        Cursor c = getReadableDatabase().rawQuery("SELECT e.id, e.name, e.muscle, "
                + "(SELECT MAX(w.start_at) FROM workout_set s JOIN workout w ON w.id = s.workout_id WHERE s.exercise_id = e.id) AS used "
                + "FROM exercise e WHERE e.name LIKE ? OR e.muscle LIKE ? ORDER BY used IS NULL, used DESC, e.name",
                new String[] {"%" + q.trim() + "%", "%" + q.trim() + "%"});
        try {
            while (c.moveToNext()) {
                Exercise e = new Exercise();
                e.id = c.getLong(0); e.name = c.getString(0 + 1); e.muscle = c.getString(2);
                out.add(e);
            }
        } finally { c.close(); }
        return out;
    }

    long addExercise(String name, String muscle) {
        SQLiteDatabase d = getWritableDatabase();
        Cursor c = d.query("exercise", new String[] {"id"}, "lower(name) = lower(?)", new String[] {name}, null, null, null);
        try { if (c.moveToFirst()) return c.getLong(0); } finally { c.close(); }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("muscle", muscle);
        v.put("custom", 1);
        return d.insert("exercise", null, v);
    }

    // ---------- workouts ----------

    private static Workout workout(Cursor c) {
        Workout w = new Workout();
        w.id = l(c, "id"); w.startAt = l(c, "start_at"); w.endAt = l(c, "end_at"); w.day = s(c, "day");
        w.type = i(c, "type"); w.name = s(c, "name"); w.fasted = i(c, "fasted") != 0; w.note = s(c, "note");
        w.durationSec = i(c, "duration_sec"); w.rpe = i(c, "rpe"); w.distanceKm = dbl(c, "distance_km"); w.kcal = dbl(c, "kcal");
        return w;
    }

    private static ContentValues values(Workout w) {
        ContentValues v = new ContentValues();
        v.put("start_at", w.startAt);
        v.put("end_at", w.endAt);
        v.put("day", dayOf(w.startAt));
        v.put("type", w.type);
        v.put("name", w.name);
        v.put("fasted", w.fasted ? 1 : 0);
        v.put("note", w.note);
        v.put("duration_min", w.durationSec / 60);
        v.put("duration_sec", w.durationSec);
        v.put("rpe", w.rpe);
        v.put("distance_km", w.distanceKm);
        v.put("kcal", w.kcal);
        return v;
    }

    /** True if a fast was running at time t. */
    boolean wasFastingAt(long t) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM fast WHERE start_at <= ? AND (end_at = 0 OR end_at > ?)",
                new String[] {String.valueOf(t), String.valueOf(t)});
        try { return c.moveToFirst() && c.getInt(0) > 0; } finally { c.close(); }
    }

    long addWorkout(Workout w) {
        w.day = dayOf(w.startAt);
        w.id = getWritableDatabase().insert("workout", null, values(w));
        return w.id;
    }

    void updateWorkout(Workout w) {
        w.day = dayOf(w.startAt);
        getWritableDatabase().update("workout", values(w), "id = ?", new String[] {String.valueOf(w.id)});
    }

    void deleteWorkout(long id) {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            d.delete("workout_set", "workout_id = ?", new String[] {String.valueOf(id)});
            d.delete("workout", "id = ?", new String[] {String.valueOf(id)});
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }

    Workout getWorkout(long id) {
        Cursor c = getReadableDatabase().query("workout", null, "id = ?", new String[] {String.valueOf(id)}, null, null, null);
        try { return c.moveToFirst() ? workout(c) : null; } finally { c.close(); }
    }

    List<Workout> workoutsForDay(String day) {
        return workouts("day = ?", new String[] {day});
    }

    List<Workout> workoutsBetween(String fromDay, String toDay) {
        return workouts("day BETWEEN ? AND ?", new String[] {fromDay, toDay});
    }

    private List<Workout> workouts(String where, String[] args) {
        List<Workout> out = new ArrayList<Workout>();
        Cursor c = getReadableDatabase().query("workout", null, where, args, null, null, "start_at");
        try { while (c.moveToNext()) out.add(workout(c)); } finally { c.close(); }
        return out;
    }

    private static WSet wset(Cursor c) {
        WSet s = new WSet();
        s.id = l(c, "id"); s.workoutId = l(c, "workout_id"); s.exerciseId = l(c, "exercise_id"); s.ord = i(c, "ord");
        s.reps = i(c, "reps"); s.weight = dbl(c, "weight"); s.done = i(c, "done") != 0; s.exerciseName = s(c, "ename");
        return s;
    }

    List<WSet> sets(long workoutId) {
        List<WSet> out = new ArrayList<WSet>();
        Cursor c = getReadableDatabase().rawQuery("SELECT s.*, e.name AS ename FROM workout_set s "
                + "JOIN exercise e ON e.id = s.exercise_id WHERE s.workout_id = ? ORDER BY s.ord, s.id",
                new String[] {String.valueOf(workoutId)});
        try { while (c.moveToNext()) out.add(wset(c)); } finally { c.close(); }
        return out;
    }

    /** Sets for an exercise from the most recent other workout that contained it. */
    List<WSet> lastSessionSets(long exerciseId, long excludeWorkoutId) {
        List<WSet> out = new ArrayList<WSet>();
        Cursor c = getReadableDatabase().rawQuery("SELECT s.*, e.name AS ename FROM workout_set s "
                + "JOIN exercise e ON e.id = s.exercise_id WHERE s.exercise_id = ? AND s.workout_id = "
                + "(SELECT s2.workout_id FROM workout_set s2 JOIN workout w ON w.id = s2.workout_id "
                + " WHERE s2.exercise_id = ? AND s2.workout_id != ? ORDER BY w.start_at DESC LIMIT 1) ORDER BY s.ord, s.id",
                new String[] {String.valueOf(exerciseId), String.valueOf(exerciseId), String.valueOf(excludeWorkoutId)});
        try { while (c.moveToNext()) out.add(wset(c)); } finally { c.close(); }
        return out;
    }

    int nextOrd(long workoutId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(ord), -1) + 1 FROM workout_set WHERE workout_id = ?",
                new String[] {String.valueOf(workoutId)});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    long addSet(WSet s) {
        ContentValues v = new ContentValues();
        v.put("workout_id", s.workoutId);
        v.put("exercise_id", s.exerciseId);
        v.put("ord", s.ord);
        v.put("reps", s.reps);
        v.put("weight", s.weight);
        v.put("done", s.done ? 1 : 0);
        s.id = getWritableDatabase().insert("workout_set", null, v);
        return s.id;
    }

    void updateSet(WSet s) {
        ContentValues v = new ContentValues();
        v.put("reps", s.reps);
        v.put("weight", s.weight);
        v.put("done", s.done ? 1 : 0);
        getWritableDatabase().update("workout_set", v, "id = ?", new String[] {String.valueOf(s.id)});
    }

    void deleteSet(long id) {
        getWritableDatabase().delete("workout_set", "id = ?", new String[] {String.valueOf(id)});
    }

    void deleteExerciseFromWorkout(long workoutId, long exerciseId) {
        getWritableDatabase().delete("workout_set", "workout_id = ? AND exercise_id = ?",
                new String[] {String.valueOf(workoutId), String.valueOf(exerciseId)});
    }

    /** {sets, done sets, volume kg, distinct exercises} for a workout. */
    double[] workoutStats(long workoutId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*), TOTAL(done), TOTAL(CASE WHEN done = 1 THEN reps * weight ELSE 0 END), "
                + "COUNT(DISTINCT exercise_id) FROM workout_set WHERE workout_id = ?", new String[] {String.valueOf(workoutId)});
        try {
            if (c.moveToFirst()) return new double[] {c.getDouble(0), c.getDouble(1), c.getDouble(2), c.getDouble(3)};
        } finally { c.close(); }
        return new double[4];
    }

    Map<String, Double> volumeByDay(String fromDay, String toDay) {
        Map<String, Double> out = new LinkedHashMap<String, Double>();
        Cursor c = getReadableDatabase().rawQuery("SELECT w.day, TOTAL(s.reps * s.weight) FROM workout w "
                + "JOIN workout_set s ON s.workout_id = w.id WHERE s.done = 1 AND w.day BETWEEN ? AND ? GROUP BY w.day",
                new String[] {fromDay, toDay});
        try { while (c.moveToNext()) out.put(c.getString(0), c.getDouble(1)); } finally { c.close(); }
        return out;
    }

    // ---------- backup ----------

    JSONObject exportTables() throws JSONException {
        JSONObject out = new JSONObject();
        SQLiteDatabase d = getReadableDatabase();
        for (String t : TABLES) {
            JSONArray rows = new JSONArray();
            Cursor c = d.query(t, null, null, null, null, null, "id");
            try {
                while (c.moveToNext()) {
                    JSONObject row = new JSONObject();
                    for (int k = 0; k < c.getColumnCount(); k++) {
                        String col = c.getColumnName(k);
                        switch (c.getType(k)) {
                            case Cursor.FIELD_TYPE_INTEGER: row.put(col, c.getLong(k)); break;
                            case Cursor.FIELD_TYPE_FLOAT: row.put(col, c.getDouble(k)); break;
                            case Cursor.FIELD_TYPE_STRING: row.put(col, c.getString(k)); break;
                            default: row.put(col, JSONObject.NULL); break;
                        }
                    }
                    rows.put(row);
                }
            } finally { c.close(); }
            out.put(t, rows);
        }
        return out;
    }

    void importTables(JSONObject tables) throws JSONException {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            for (String t : TABLES) {
                d.delete(t, null, null);
                JSONArray rows = tables.optJSONArray(t);
                if (rows == null) continue;
                for (int r = 0; r < rows.length(); r++) {
                    JSONObject row = rows.getJSONObject(r);
                    ContentValues v = new ContentValues();
                    Iterator<String> keys = row.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        Object val = row.get(k);
                        if (val == JSONObject.NULL) v.putNull(k);
                        else if (val instanceof Integer || val instanceof Long) v.put(k, ((Number) val).longValue());
                        else if (val instanceof Number) v.put(k, ((Number) val).doubleValue());
                        else v.put(k, val.toString());
                    }
                    d.insertOrThrow(t, null, v);
                }
            }
            fillDurations(d);
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }

    void deleteAll() {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            for (String t : TABLES) if (!t.equals("exercise")) d.delete(t, null, null);
            d.delete("exercise", "custom = 1", null);
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }

    static String today() { return LocalDate.now().toString(); }
}
