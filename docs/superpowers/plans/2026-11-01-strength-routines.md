# Strength Routines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add routines to strength logs: a Routine card, an expand-to-edit exercise editor, saved and auto-saved routines, and a Routines screen reached from Workouts.

**Architecture:** Pure routine rules live in `FastingLogic` around a plain `Lift` class. `Db` stores saved routines and a separate copy per workout in three new tables. New screens receive lists and return results through callbacks, because `LogWorkoutScreen` stays alive on the stack while they are open.

**Tech Stack:** Java 8 source level, Android views built in code with `Ui` helpers, SQLite through `SQLiteOpenHelper`. No third-party libraries.

**Spec:** `docs/superpowers/specs/2026-11-01-strength-routines-design.md`

## Global Constraints

- Sets 1–20, reps 1–100, weight 0–300 kg in 0.5 kg steps. Weight 0 displays as `Bodyweight`.
- New exercise defaults: 3 sets, 10 reps, Bodyweight.
- Recent keeps the latest 20 unnamed routines.
- The database goes from version 3 to 4. Existing tables don't change.
- The new tables join `Db.TABLES` only. `FastingLogic.BACKUP_TABLES` and `BACKUP_VERSION` don't change.
- `FastingLogic` stays free of Android types, because `tests/run.sh` compiles it alone.
- A workout's copy never changes when a saved routine changes.
- Keep the code style: anonymous classes rather than lambdas, Java 8 only, brief comments.
- Commit to `main` with plain imperative messages like the existing history. Don't push.

## Review Focus

1. **Non-ASCII exercise and routine names.** SQLite `lower()` folds ASCII only, so `ЖИМ` and `жим` must be matched in Java through `FastingLogic.nameKey`, never in SQL. Pinned in Task 1.
2. **Device locale.** A decimal-comma or Turkish locale must not change a routine's fingerprint, or duplicates appear after a language change. Pinned in Task 1.
3. **Names containing separators.** A name with `|`, `;` or digits must not make two different routines share a fingerprint. Pinned in Task 1.
4. **Switching rows in the editor.** Collapsing a row after typing a name or moving a dial keeps those values. Implemented in Task 3 and checked by hand in Task 6.
5. **Very long exercise names.** The name truncates on the card and in rows, and the `4 × 8 · 60 kg` text stays visible. Implemented in Task 5 and checked by hand in Task 6.

---

### Task 1: Routine logic in FastingLogic

**Files:**
- Modify: `src/app/fastwell/FastingLogic.java`
- Test: `tests/FastingLogicTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `static final int SETS_MIN = 1, SETS_MAX = 20, REPS_MIN = 1, REPS_MAX = 100, LIFT_SETS_DEFAULT = 3, LIFT_REPS_DEFAULT = 10, RECENT_ROUTINES = 20`
  - `static final double LIFT_KG_MAX = 300, LIFT_KG_STEP = 0.5`
  - `static final class Lift { String name = ""; int sets = LIFT_SETS_DEFAULT, reps = LIFT_REPS_DEFAULT; double weight; Lift copy(); }`
  - `static String nameKey(String name)`
  - `static String routineFingerprint(List<Lift> lifts)`
  - `static int routineSets(List<Lift> lifts)`
  - `static double routineVolume(List<Lift> lifts)`
  - `static String kgText(double weight)`
  - `static String liftLine(Lift l)`
  - `static String routineSummary(List<Lift> lifts)`
  - `static String routineTitle(List<Lift> lifts)`

- [ ] **Step 1: Write the failing tests**

Add this block to `main` before the final `System.out.println`, with a `lift` helper beside `done`:

```java
static FastingLogic.Lift lift(String name, int sets, int reps, double weight) {
    FastingLogic.Lift l = new FastingLogic.Lift();
    l.name = name; l.sets = sets; l.reps = reps; l.weight = weight;
    return l;
}
```

```java
// routines
Locale locale = Locale.getDefault();
Locale.setDefault(Locale.US);
List<FastingLogic.Lift> push = Arrays.asList(lift("Bench press", 4, 8, 60), lift("Dips", 4, 12, 0));
eq("bench press", FastingLogic.nameKey("  Bench   PRESS "), "name key folds case and spaces");
eq(FastingLogic.nameKey("жим лежа"), FastingLogic.nameKey("ЖИМ  ЛЕЖА"), "name key folds Cyrillic");
eq(FastingLogic.routineFingerprint(push),
        FastingLogic.routineFingerprint(Arrays.asList(lift(" bench  PRESS", 4, 8, 60), lift("dips", 4, 12, 0))),
        "case and spacing don't change a fingerprint");
eq(false, FastingLogic.routineFingerprint(push).equals(FastingLogic.routineFingerprint(
        Arrays.asList(lift("Dips", 4, 12, 0), lift("Bench press", 4, 8, 60)))), "order changes a fingerprint");
eq(false, FastingLogic.routineFingerprint(push).equals(FastingLogic.routineFingerprint(
        Arrays.asList(lift("Bench press", 5, 8, 60), lift("Dips", 4, 12, 0)))), "sets change a fingerprint");
eq(false, FastingLogic.routineFingerprint(push).equals(FastingLogic.routineFingerprint(
        Arrays.asList(lift("Bench press", 4, 9, 60), lift("Dips", 4, 12, 0)))), "reps change a fingerprint");
eq(false, FastingLogic.routineFingerprint(push).equals(FastingLogic.routineFingerprint(
        Arrays.asList(lift("Bench press", 4, 8, 60.5), lift("Dips", 4, 12, 0)))), "weight changes a fingerprint");
eq(false, FastingLogic.routineFingerprint(Arrays.asList(lift("A|4|8|0;B", 1, 1, 0))).equals(
        FastingLogic.routineFingerprint(Arrays.asList(lift("A", 4, 8, 0), lift("B", 1, 1, 0)))),
        "separators in a name can't collide");
String usFingerprint = FastingLogic.routineFingerprint(Arrays.asList(lift("Incline press", 3, 10, 57.5)));
Locale.setDefault(new Locale("tr", "TR"));
eq(usFingerprint, FastingLogic.routineFingerprint(Arrays.asList(lift("INCLINE PRESS", 3, 10, 57.5))),
        "a fingerprint doesn't depend on the device locale");
Locale.setDefault(Locale.US);
eq(8, FastingLogic.routineSets(push), "sets add up");
eq(1920.0, FastingLogic.routineVolume(push), "bodyweight adds no volume");
eq("Bodyweight", FastingLogic.kgText(0), "zero weight is bodyweight");
eq("60 kg", FastingLogic.kgText(60), "whole kilos");
eq("57.5 kg", FastingLogic.kgText(57.5), "half kilos");
eq("4 × 8 · 60 kg", FastingLogic.liftLine(lift("Bench press", 4, 8, 60)), "lift line");
eq("4 × 12 · Bodyweight", FastingLogic.liftLine(lift("Dips", 4, 12, 0)), "bodyweight lift line");
eq("2 exercises  -  8 sets  -  1,920 kg", FastingLogic.routineSummary(push), "routine summary");
eq("1 exercise  -  1 set", FastingLogic.routineSummary(Arrays.asList(lift("Plank", 1, 1, 0))), "summary with no volume");
eq("Bench press, Dips", FastingLogic.routineTitle(push), "two exercises are both named");
eq("A", FastingLogic.routineTitle(Arrays.asList(lift("A", 1, 1, 0))), "one exercise");
eq("A, B, +2", FastingLogic.routineTitle(Arrays.asList(lift("A", 1, 1, 0), lift("B", 1, 1, 0),
        lift("C", 1, 1, 0), lift("D", 1, 1, 0))), "the rest are counted");
eq(null, FastingLogic.backupProblem(FastingLogic.BACKUP_VERSION, Arrays.asList(FastingLogic.BACKUP_TABLES)),
        "a backup without routines restores");
Locale.setDefault(locale);
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `bash tests/run.sh`
Expected: compile errors for `FastingLogic.Lift` and the new functions.

- [ ] **Step 3: Implement the logic in a new `// ---------- routines ----------` section of `FastingLogic.java`**

- `nameKey`: trim, replace `\\s+` with one space, `toLowerCase(Locale.ROOT)`.
- `routineFingerprint`: for each lift append `nameKey`, sets, reps and `Math.round(weight * 2)` as integers, separated by `\u001f` and ended by `\u001e`. Build with `StringBuilder.append(int/long)` so no locale applies.
- `kgText`: `Bodyweight` when `weight <= 0`. Otherwise `%.0f kg` for whole values and `%.1f kg` otherwise, in the default locale.
- `routineSummary`: singular `exercise` and `set` for 1. Leave out the kg part at zero volume. Format kg with `String.format(Locale.getDefault(), "%,.0f kg", v)`. Parts join with two spaces, a hyphen and two spaces.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `bash tests/run.sh`
Expected: `N checks, 0 failures`, with N above 250.

- [ ] **Step 5: Commit**

```bash
git add src/app/fastwell/FastingLogic.java tests/FastingLogicTest.java
git commit -m "Add routine logic"
```

### Task 2: Routine storage in Db

**Files:**
- Modify: `src/app/fastwell/Db.java`

**Interfaces:**
- Consumes: `FastingLogic.Lift`, `nameKey`, `routineFingerprint`, `routineVolume`, `RECENT_ROUTINES`.
- Produces:
  - `static final class Routine { long id, lastUsed; String name = ""; List<FastingLogic.Lift> lifts = new ArrayList<FastingLogic.Lift>(); boolean named(); }`
  - `List<Routine> routines(boolean named)`: last used first, lifts loaded.
  - `Routine getRoutine(long id)`: null if missing.
  - `Routine namedRoutine(String name)`: matched by `nameKey`, null if none.
  - `long saveRoutine(String name, List<FastingLogic.Lift> lifts)`: Save as. Returns the routine's id.
  - `long updateRoutine(long id, List<FastingLogic.Lift> lifts)`: returns the id that now holds these lifts.
  - `void renameRoutine(long id, String name)`
  - `void deleteRoutine(long id)`
  - `void restoreRoutine(Routine r)`: undo, keeps `r.id`, name and last used.
  - `void rememberRoutine(List<FastingLogic.Lift> lifts)`: auto-save.
  - `List<FastingLogic.Lift> workoutLifts(long workoutId)`
  - `void setWorkoutLifts(long workoutId, List<FastingLogic.Lift> lifts)`
  - `FastingLogic.Lift lastLift(String name, long excludeWorkoutId)`: null if none.
  - `List<String> liftNames()`

- [ ] **Step 1: Add the schema**

Bump the helper to version 4. Add `createRoutines(SQLiteDatabase d)` with the spec's three tables and two indexes, called from `onCreate` and from `onUpgrade` under `if (oldV < 4)`. Append `"routine", "routine_exercise", "workout_exercise"` to `TABLES`.

- [ ] **Step 2: Implement the routine queries**

Rules from the spec, each multi-statement write in one transaction:

- `saveRoutine`: an existing `namedRoutine(name)` is overwritten in place and keeps its id. Otherwise, a matching unnamed fingerprint is named and kept. Otherwise a new named row is inserted.
- `updateRoutine`: an unnamed routine that now matches another routine is deleted, the match's last used is set to now, and the match's id is returned.
- After any write that leaves a named routine, delete unnamed routines with the same fingerprint.
- `rememberRoutine`: on a fingerprint match, prefer a named row and set its last used to now. Otherwise insert an unnamed row. Then delete unnamed rows beyond the newest `RECENT_ROUTINES`.

Compare names only through `FastingLogic.nameKey` in Java. Don't use SQL `lower()` here.

- [ ] **Step 3: Implement workout copies and lookups**

- `setWorkoutLifts`: delete then insert with `ord` from list position.
- `deleteWorkout`: also delete from `workout_exercise` in its transaction.
- `workoutStats`: add `TOTAL(sets * reps * weight)` from `workout_exercise` to index 2. The other indexes don't change.
- `lastLift`: query `workout_exercise` joined to `workout` where the workout id isn't excluded, ordered by `w.start_at DESC, we.ord`. Return the first row whose `nameKey` matches, and pass 0 to exclude nothing.
- `liftNames`: names from `workout_exercise` by workout start descending, then `routine_exercise` by routine last used descending, then `exercise` by name. Keep the first spelling for each `nameKey`.

- [ ] **Step 4: Verify the app compiles and the tests pass**

Run: `./build.sh && bash tests/run.sh`
Expected: the build writes `out/Fastwell-1.1.0.apk`, and the tests report `0 failures`. `Db` has no automated tests in this repo. Task 6 covers it by hand.

- [ ] **Step 5: Commit**

```bash
git add src/app/fastwell/Db.java
git commit -m "Store routines and each workout's copy"
```

### Task 3: Routine editor

**Files:**
- Create: `src/app/fastwell/RoutineEditScreen.java`
- Modify: `src/app/fastwell/Dial.java`, `src/app/fastwell/Screen.java`, `src/app/fastwell/MainActivity.java:435-442`

**Interfaces:**
- Consumes: `Lift`, the dial limits, `kgText`, `liftLine`, `routineFingerprint`, `Db.lastLift`, `Db.liftNames`.
- Produces:
  - `boolean Screen.onBack()`: default false. True means the screen handled back.
  - `RoutineEditScreen(MainActivity a, String title, List<FastingLogic.Lift> start, long excludeWorkoutId, boolean needsOne, Done done)`
  - `interface RoutineEditScreen.Done { void on(List<FastingLogic.Lift> lifts); }`. The editor calls this with trimmed, named lifts and doesn't pop itself; the callback decides.
  - The editor copies `start`, so cancelling never touches the caller's list.

- [ ] **Step 1: Make Dial fit wide text**

In `Dial.onDraw`, shrink a value's text size when its measured width exceeds the column width minus 8dp on each side. Scale by the width ratio, for both the rolling values and `numW`. Values that already fit must draw exactly as before.

- [ ] **Step 2: Add the back hook**

Add `boolean onBack() { return false; }` to `Screen`. In `MainActivity.handleBack()`, return early when `screen != null && screen.onBack()`, after the onboarding check. The header arrow and the system gesture both go through `handleBack()`.

- [ ] **Step 3: Implement `RoutineEditScreen`**

Follow the layout pattern of `LogWorkoutScreen`: `backHeader`, scrolling column, bottom bar with `Ui.primary(c, "Done", R.color.train)`, `showsNav()` false.

- Rows are collapsed cards. The expanded index is a field, and only that row holds a `Dial`. Rebuild the list when it changes, using the same `Lift` objects, so typed names and dial values persist.
- The name field is an `AutoCompleteTextView` styled with `Ui.styleField`, using `Db.liftNames()` in an `ArrayAdapter` and a threshold of 1. It writes to the `Lift` as you type and refreshes the hint.
- Dial columns: `addColumn("SETS", "", SETS_MIN, SETS_MAX, 1, 0, ...)`, `addColumn("REPS", "", REPS_MIN, REPS_MAX, 1, 0, ...)`, `addColumn("KG", "", 0, LIFT_KG_MAX, LIFT_KG_STEP, 1, ...)`. The KG column's format is `Bodyweight` at zero and `Ui.plain(v)` otherwise.
- Hint text: `"Last time: " + liftLine(last)`, hidden when `lastLift` is null or the name is blank.
- Row actions: Move up, Move down, Duplicate, Remove. Duplicate inserts `copy()` below and expands it. Add exercise appends a `new Lift()`, expands it and focuses the name.
- Done: trim names, drop blanks, and call `done.on`. When `needsOne` is true, Done is disabled until a row has a name.
- `onBack()`: compare the fingerprint of the current list with the start. If they differ, show a dialog with title `Discard changes?`, buttons `Keep editing` and `Discard`, and return true. Discard calls `a.pop()`. If they match, return false.
- A collapsed row with a blank name shows `No name yet`.

- [ ] **Step 4: Verify the app compiles**

Run: `./build.sh`
Expected: the build succeeds. The screen has no caller until Task 4.

- [ ] **Step 5: Commit**

```bash
git add src/app/fastwell/RoutineEditScreen.java src/app/fastwell/Dial.java src/app/fastwell/Screen.java src/app/fastwell/MainActivity.java
git commit -m "Add the routine editor"
```

### Task 4: Routines screen and picker

**Files:**
- Create: `src/app/fastwell/RoutinePickerScreen.java`, `src/app/fastwell/RoutineDialogs.java`
- Modify: `src/app/fastwell/WorkoutsScreen.java:37`, `src/app/fastwell/Ui.java`

**Interfaces:**
- Consumes: `Db.routines`, `getRoutine`, `namedRoutine`, `saveRoutine`, `updateRoutine`, `renameRoutine`, `deleteRoutine`, `restoreRoutine`, `RoutineEditScreen`, `routineTitle`, `routineSummary`, `routineSets`.
- Produces:
  - `RoutinePickerScreen(MainActivity a, Pick pick)`: a null `pick` means manage mode.
  - `interface RoutinePickerScreen.Pick { void on(Db.Routine r); void newRoutine(); }`. The picker pops itself before calling either method.
  - `RoutineDialogs.saveAs(MainActivity a, List<FastingLogic.Lift> lifts, Saved then)`
  - `interface RoutineDialogs.Saved { void on(long id, String name); }`
  - `RoutineDialogs.rename(MainActivity a, Db.Routine r, Runnable then)`
  - `static String Ui.shortDate(long t)`: pattern `d MMM` in the default locale.

- [ ] **Step 1: Implement `RoutineDialogs`**

- `saveAs`: a name field in `Ui.dialogBody`, with the Save button disabled while the name is blank. Save trims the name. If `namedRoutine(name)` exists, confirm with title `Replace <name>?`. Then call `db.saveRoutine` and `then.on`. Cancel does nothing and leaves the screen as it was.
- `rename`: same field, titled `Name it` for an unnamed routine and `Rename` otherwise. A clash with a different routine shows `That name is already used` in the dialog and keeps it open. Renaming to the same name with different case is allowed.

- [ ] **Step 2: Implement `RoutinePickerScreen`**

- Title: `Choose routine` in pick mode, `Routines` in manage mode.
- Section titles `Saved` and `Recent`, hidden when empty. If both are empty, show `Routines appear here once you log or save one.`
- Saved rows show the name and `routineSummary`. Recent rows show `routineTitle` and `"Used " + Ui.shortDate(r.lastUsed) + "  -  " + routineSets + " sets"`, with singular `set` for 1.
- Row tap: `pick.on(r)` in pick mode, the same as Edit in manage mode.
- ⋮ opens an `android.widget.PopupMenu` with `Name it` or `Rename`, `Edit` and `Delete`.
- Edit opens `RoutineEditScreen` with `excludeWorkoutId` 0 and `needsOne` true. Its Done calls `db.updateRoutine` then `a.pop()`.
- Delete calls `db.deleteRoutine` and shows `a.snack("Routine deleted", undo)`, where undo calls `db.restoreRoutine(r)`. `MainActivity.snack` refreshes the screen after undo. Call `a.refresh()` after deleting.
- The pinned `New routine` button calls `pick.newRoutine()` in pick mode. In manage mode it opens the editor empty with `needsOne` true, and Done calls `RoutineDialogs.saveAs` then `a.pop()`, so cancelling the dialog stays in the editor.

- [ ] **Step 3: Add the Routines button to `WorkoutsScreen`**

Use the top row from `FastScreen.java:30-39`: an hbox holding `Ui.header` with weight 1, plus `Ui.tonal(c, "Routines", R.color.train)` at `Ui.lp(Ui.WRAP, Ui.dp(c, 40))`. It pushes `new RoutinePickerScreen(a, null)`.

- [ ] **Step 4: Verify the app compiles**

Run: `./build.sh`
Expected: the build succeeds.

- [ ] **Step 5: Commit**

```bash
git add src/app/fastwell/RoutinePickerScreen.java src/app/fastwell/RoutineDialogs.java src/app/fastwell/WorkoutsScreen.java src/app/fastwell/Ui.java
git commit -m "Add the Routines screen"
```

### Task 5: Routine card on Log strength

**Files:**
- Create: `src/app/fastwell/RoutineCard.java`
- Modify: `src/app/fastwell/LogWorkoutScreen.java`, `src/app/fastwell/WorkoutsScreen.java:110-116`

**Interfaces:**
- Consumes: `RoutinePickerScreen`, `RoutinePickerScreen.Pick`, `RoutineEditScreen`, `RoutineDialogs.saveAs`, `Db.workoutLifts`, `setWorkoutLifts`, `rememberRoutine`, `updateRoutine`, `getRoutine`, `routineFingerprint`, `routineSummary`, `liftLine`, `routineSets`.
- Produces:
  - `static View RoutineCard.create(Context c, String title, List<FastingLogic.Lift> lifts, String updateName, Actions actions)`: a null `updateName` hides the Update button.
  - `interface RoutineCard.Actions { void choose(); void create(); void edit(); void saveAs(); void update(); }`

- [ ] **Step 1: Implement `RoutineCard`**

Match the empty and filled states in the spec. Each exercise is one line with the name on the left, weight 1 and ellipsized at the end, and `liftLine` on the right, so a long name never pushes the numbers off screen. **Choose saved** calls `choose`, **New routine** calls `create`, **Edit** calls `edit`, **Change** calls `choose`, **Save as** calls `saveAs`. The Update button reads `"Update " + updateName`.

- [ ] **Step 2: Add the draft to `LogWorkoutScreen`**

New fields:

```java
private List<FastingLogic.Lift> lifts = new ArrayList<FastingLogic.Lift>();
/** The title the routine gives the workout; blank until it has one. */
private String routineName = "";
/** The saved routine chosen in this form, 0 if none, and its fingerprint at that point. */
private long sourceId;
private String sourceFingerprint = "", loadedFingerprint = "";
private LinearLayout routineBox;
```

In `load()`, for strength workouts that aren't new, read `db.workoutLifts(w.id)` into `lifts`, set `loadedFingerprint`, and set `routineName` to `w.name` unless it equals `Strength workout`.

- [ ] **Step 3: Show the card and wire its actions**

In `build()`, for strength only, add `Ui.sectionTitle(c, "Routine")` and a `routineBox` directly after the duration dial. A `showRoutine()` method empties and refills `routineBox`, so actions without a screen change can update it.

- Title: `Routine` when `routineName` is blank. Otherwise `routineName`, plus `, edited` when `sourceId > 0` and the fingerprint differs from `sourceFingerprint`.
- `updateName`: `routineName` when the fingerprint differs, `sourceId > 0` and `db.getRoutine(sourceId) != null`. Otherwise null.
- Choosing a saved routine copies its lifts, and sets `routineName`, `sourceId` and `sourceFingerprint`. Choosing a recent one copies its lifts and clears those three.
- `newRoutine()` pushes the editor on an empty list.
- Edit opens the editor with `w.id` as `excludeWorkoutId` and `needsOne` false. Done sets `lifts`, then pops. An empty result clears `routineName` and `sourceId`.
- Save as: on success, set `routineName`, `sourceId` and `sourceFingerprint`, then `showRoutine()`.
- Update: `db.updateRoutine(sourceId, lifts)`, set `sourceFingerprint`, then `showRoutine()`.

- [ ] **Step 4: Save and undo the copy**

In `save()`, for strength:

- Set `w.name` to `routineName`, or `Strength workout` if `lifts` is empty or `routineName` is blank.
- After `addWorkout` or `updateWorkout`, call `db.setWorkoutLifts(w.id, lifts)`.
- Call `db.rememberRoutine(lifts)` when `lifts` isn't empty and either `isNew` or the fingerprint differs from `loadedFingerprint`.

In `delete()`, capture `db.workoutLifts(w.id)` before deleting, and pass it to `setWorkoutLifts` with the new id in the Undo runnable.

- [ ] **Step 5: Add the set count to the summary row**

In `WorkoutsScreen.summaryRow`, for strength workouts, add `", 14 sets"` (singular `set` for 1) from `routineSets(a.db.workoutLifts(w.id))` before the kg part, when above zero. Update the comment above that code, which says only older workouts have a volume.

- [ ] **Step 6: Update the class comment**

`LogWorkoutScreen`'s Javadoc says strength is only its duration for now. Replace it to say strength can also have a routine.

- [ ] **Step 7: Verify the app compiles and the tests pass**

Run: `./build.sh && bash tests/run.sh`
Expected: the build succeeds and the tests report `0 failures`.

- [ ] **Step 8: Commit**

```bash
git add src/app/fastwell/RoutineCard.java src/app/fastwell/LogWorkoutScreen.java src/app/fastwell/WorkoutsScreen.java
git commit -m "Add the routine card to strength logs"
```

### Task 6: Docs and device checks

**Files:**
- Modify: `README.md:37-46`, `docs/images/demo/12-log-strength.png`

**Interfaces:**
- Consumes: the finished app. `adb` isn't installed on this machine, so the user does the device pass by installing `out/Fastwell-1.1.0.apk`.
- Produces: nothing for later tasks.

- [ ] **Step 1: Update the README**

Change the Workouts section's strength bullet and add one bullet saying strength sessions can record a routine, with saved and recent routines to reuse.

- [ ] **Step 2: Hand over the device checks**

Ask the user to run these on a device or emulator and report failures. Fix any failure in the code it belongs to, and commit the fix separately.

- Upgrade over an existing 1.1.0 install. Existing workouts, totals and old kg figures are intact.
- Log with a new routine. It appears in Recent and the Workouts row shows its sets and kg.
- Choose a saved routine, change it, and see `Push day, edited` and Update. Update it and confirm the older log didn't change.
- Save as with a new name and with a clashing name. Name it and Rename with a clash. Try a Cyrillic name in different case.
- Log the same routine twice, in different letter case. One entry remains. Log 21 distinct routines. Recent stops at 20.
- In the editor, type a name and move a dial in one row, expand another, and confirm the first kept both. Check reorder, duplicate, remove, Done with blank names, and the hint and name suggestions.
- Cancel with changes, on both the arrow and the system gesture.
- Routines button: open, edit, rename, delete with Undo, and New routine including cancelling its name dialog.
- Delete a log, then Undo. Its routine returns and its Recent entry stayed.
- Back up and restore, restore a pre-routine backup, and use Delete all data.
- A very long exercise name truncates and the numbers stay visible.
- `Bodyweight` fits the dial on a narrow screen, in light and dark themes.

- [ ] **Step 3: Replace the screenshot**

Ask the user for a new Log strength screenshot showing the Routine card, and save it as `docs/images/demo/12-log-strength.png`. The repo has no way to capture one without a device.

- [ ] **Step 4: Commit**

```bash
git add README.md docs/images/demo/12-log-strength.png
git commit -m "Describe routines in the README"
```
