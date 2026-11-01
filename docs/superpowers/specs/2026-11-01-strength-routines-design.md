# Strength routines design

## Goal

Let a strength log record what the session contained, and make repeat sessions quick to enter. A log gets a routine: a list of exercises, each with a name, sets, reps and weight. Every logged routine stays available to reuse, and any routine can be kept under a name.

Success means:

- A repeat session takes a few taps: choose the routine, adjust anything that changed, save.
- A session can be read back exercise by exercise.
- Past logs never change when a saved routine is edited.
- Routines are optional. Logging strength without one works as it does today.

## Out of scope

- Exercise progress charts and personal record badges.
- A live workout mode with set tick-offs and a rest timer.
- Different reps or weight per set. One exercise is one row of sets × reps at one weight.
- Routines on cardio workouts.
- Rewriting legacy `workout_set` rows.
- Keeping an unsaved new log if Android kills the app. New logs are already lost in that case.

## User-facing behaviour

### Routine card on Log strength

A Routine section sits directly after Duration on the strength form, on both new logs and edits. Cardio is unchanged.

- **Empty:** "No routine added", with **Choose saved** and **New routine** buttons.
- **Filled:** the title, a summary line and one line per exercise, then **Edit**, **Change** and **Save as** buttons. Example summary: `4 exercises  -  14 sets  -  3,120 kg`. Example line: `Bench press  4 × 8 · 60 kg`, or `Dips  4 × 12 · Bodyweight`.

Card title:

| State | Title |
|---|---|
| Chosen saved routine, unchanged | `Push day` |
| Chosen saved routine, changed in this log | `Push day, edited`, plus an **Update Push day** button |
| Chosen recent routine, or a new one | `Routine` |
| Opening an older log | The workout's name, or `Routine` if that is `Strength workout` |

**Update** only appears when the routine was chosen in the current form session. If the source routine has since been deleted, the button goes away.

Removing every exercise in the editor returns the card to empty.

Back on Log strength asks before discarding when the routine changed.

The calorie estimate still uses only duration, RPE and body weight.

### Routine picker

**Choose saved** and **Change** open Choose routine:

- **Saved:** named routines, most recently used first.
- **Recent:** auto-saved routines without a name, most recently used first, at most 20. They are titled by their first two exercises, like `Back squat, Leg press, +2`, and show `Used 28 Oct  -  14 sets`.
- A **New routine** button is pinned at the bottom.
- If both lists are empty, a short message says routines appear here once logged.

If the log's source routine is renamed or edited from the picker, the log picks up the new name, and an unchanged draft picks up the new exercises.

Tapping a row copies that routine into the log and returns to it. **New routine** closes the picker and opens the editor on an empty list.

Each row has a ⋮ menu:

- **Name it** on a recent routine, or **Rename** on a saved one.
- **Edit**, which opens the editor on the stored routine.
- **Delete**, with an Undo snackbar. A log already using its copy keeps it.

### Routines screen

A **Routines** button sits at the top right of the Workouts screen header, on every day shown. It opens the Routines screen, which manages routines without starting a log.

This is the picker in a second mode, with the same Saved and Recent lists, empty message, ⋮ menu and pinned **New routine** button. Two things differ:

- The title is `Routines`.
- Tapping a row opens the editor on the stored routine, the same as ⋮ **Edit**.

**New routine** here opens the editor on an empty list. On **Done** it asks for a name, then adds the routine to Saved. Cancelling that dialog stays in the editor. Name clashes offer to replace, the same as **Save as**.

### Routine editor

The editor is one scrolling list of compact rows, and one exercise is expanded at a time. Tapping a row expands it and collapses the others. It works in two modes with the same UI: on a log's copy, or on a stored routine.

A collapsed row shows the name and `4 × 8 · 60 kg`. A row with no name shows `No name yet`.

An expanded row has:

- A name field with suggestions.
- A three-column dial: **SETS** 1–20, **REPS** 1–100, **KG** Bodyweight, then 0.5–300 in 0.5 kg steps.
- A hint such as `Last time: 4 × 8 · 57.5 kg`, when that exercise has been logged before.
- **Move up**, **Move down**, **Duplicate** and **Remove**. Move up and Move down are disabled at the ends of the list.

**Add exercise** appends a row with 3 sets, 10 reps and Bodyweight, expands it and focuses the name field. **Duplicate** inserts a copy below the current row and expands it.

**Done** trims names and drops rows with a blank name.

- In log mode, Done returns the result to the log. No rows left means the card is empty.
- In stored mode, Done is disabled until at least one row has a name.

The back arrow and the system back gesture cancel. If anything changed, a dialog offers **Keep editing** and **Discard**.

### Exercise name suggestions

Typing in the name field suggests matching names, ignoring case. Names you have used come first, most recent first, followed by the seeded names in the `exercise` table. Each name appears once. The field stays free text and a suggestion is never required.

### Last time hint

The hint comes from the most recent strength workout containing that exercise, matched on the trimmed name, ignoring case. The workout being edited doesn't count. If that workout has the exercise twice, the first row is used. The hint updates as the name changes, and it never changes the dial values.

### Saving and naming

- **Save as** asks for a name and stores the routine immediately. Saving the log isn't required.
- **Update Push day** overwrites the saved routine with this log's routine immediately. The title goes back to `Push day`.
- Routine names are unique among saved routines, ignoring case. If **Save as** hits an existing name, it asks whether to replace that routine. **Name it** and **Rename** show an error in the dialog instead.
- Replacing keeps the replaced routine's id, so a log that chose it can still offer **Update**.

### Auto-save on logging

On saving a strength log with at least one exercise:

1. The log's copy is written to `workout_exercise`, replacing any earlier copy.
2. The routine is looked up by fingerprint. A match has its last-used time set to now, preferring a saved routine. Otherwise a new unnamed routine is created.
3. Recent is trimmed to the latest 20 unnamed routines.

When editing an older log, steps 2 and 3 run only if the routine changed.

Duplicates are resolved like this:

- **Save as** on content that matches an unnamed routine names that row.
- **Save as** on content that matches a saved routine creates a second saved routine, because a different name is a deliberate choice.
- After any saved routine changes, unnamed routines with the same fingerprint are deleted.
- If editing a stored unnamed routine makes it match another routine, the edited row is deleted and the match's last-used time is set to now.

### Workout name and lists

On saving, a routine that came from a saved routine sets `w.name`, even if it was edited in this log. A routine saved with **Save as** does the same. Otherwise the name is `Strength workout`. Removing a log's routine resets the name to `Strength workout`. A log that never had a routine keeps its existing name unless a saved routine is chosen.

The shared summary row adds the set count before kg, as in `1 h, 347 kcal, 14 sets, 3,120 kg`. The full breakdown is on the Routine card when a session is opened.

## Data model

The database goes from version 3 to 4. New tables are created in `onCreate` and in `onUpgrade` for `oldV < 4`. Existing tables don't change.

```sql
CREATE TABLE routine (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL DEFAULT '',
  fingerprint TEXT NOT NULL,
  last_used INTEGER NOT NULL
);
CREATE TABLE routine_exercise (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  routine_id INTEGER NOT NULL,
  ord INTEGER NOT NULL,
  name TEXT NOT NULL,
  sets INTEGER NOT NULL,
  reps INTEGER NOT NULL,
  weight REAL NOT NULL DEFAULT 0
);
CREATE TABLE workout_exercise (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  workout_id INTEGER NOT NULL,
  ord INTEGER NOT NULL,
  name TEXT NOT NULL,
  sets INTEGER NOT NULL,
  reps INTEGER NOT NULL,
  weight REAL NOT NULL DEFAULT 0
);
CREATE INDEX idx_routine_exercise_routine ON routine_exercise(routine_id);
CREATE INDEX idx_workout_exercise_workout ON workout_exercise(workout_id);
```

An empty `name` means an auto-saved routine. A weight of 0 means Bodyweight.

A workout doesn't store which routine it came from. Its copy stands alone, and the link to the source exists only in the open form.

The fingerprint covers each exercise in order: its name, trimmed and lowercased with runs of spaces collapsed, then sets, reps and weight. The routine name isn't part of it.

## Code structure

| Unit | Change |
|---|---|
| `FastingLogic` | Plain `Lift` class (name, sets, reps, weight). Dial limits. Pure functions for fingerprint, total sets, volume, exercise line, summary and recent title. No Android types, so the tests can call it directly. |
| `Db` | `Routine` model, new tables and queries: routines by kind, get, save, rename, delete, restore, auto-save, trim Recent, read and replace a workout's copy, last-time lookup, name suggestions. `deleteWorkout` also removes the copy. `workoutStats` adds volume from `workout_exercise` to its total. |
| `RoutineCard` (new) | Builds the card for a draft and reports button taps. `LogWorkoutScreen` is already 340 lines, so this stays separate. |
| `RoutineEditScreen` (new) | The editor. Takes a list of lifts and a done callback, and doesn't know where they came from. |
| `RoutineDialogs` (new) | The Save as and rename dialogs, shared by the card and the picker. |
| `RoutinePickerScreen` (new) | The Saved and Recent lists and row menus. Runs in pick mode from a log, or manage mode as the Routines screen. |
| `LogWorkoutScreen` | Holds the draft and its source, shows the card, writes the copy and auto-saves. Undoing a delete restores the copy. |
| `WorkoutsScreen` | Adds the **Routines** button to the header and the set count to the summary row. |
| `Dial` | Shrinks a value's text when it is wider than its column. `Bodyweight` doesn't fit a three-column dial at the current size. |
| `Screen`, `MainActivity` | `Screen.onBack()` returns false by default, and `handleBack()` asks it first, so the arrow and the system gesture share the discard check. |

`LogWorkoutScreen` is kept alive on the stack while the picker or editor is open, and its fields survive the rebuild when it returns. So the new screens hand back results through callbacks and don't need new state in `MainActivity`.

## Backup and deletion

- The three tables join `Db.TABLES`, so export, restore and Delete all data include them.
- They stay out of `FastingLogic.BACKUP_TABLES`, because older backups don't have them. Restoring such a backup leaves no routines.
- `BACKUP_VERSION` stays at 1, as it did for `weight_log`. An older app version restoring a newer backup ignores the new tables.
- Deleting a workout removes its copy, and Undo restores it. Its auto-saved routine stays in Recent, because it is a reusable routine and not part of the log.
- Legacy `workout_set` rows keep contributing to `workoutStats`, so old kg totals stay. Old workouts show an empty card.

## Testing

Automated tests in `tests/FastingLogicTest.java` cover:

- Fingerprints: same for different case and spacing, different for changed order, sets, reps or weight.
- Totals: set count and volume, with Bodyweight adding no volume.
- Text: exercise lines including Bodyweight and half kilos, summary line, recent title with two or fewer exercises and with more.
- Backup: a backup without the new tables still passes `backupProblem`.

The repo has no automated tests for `Db` or screens, so these are checked by hand on a device or emulator:

- Upgrade from a version 3 database with existing data.
- Log, choose, edit, Save as, Update and Name it flows, including name clashes.
- The Routines button: open, edit, rename, delete with Undo, and New routine including cancelling its name dialog.
- Duplicate handling and Recent stopping at 20.
- Reorder, duplicate, remove, Done with blank names, and discard on both the arrow and the system gesture.
- Editing a saved routine leaves past logs unchanged.
- Delete a log, then Undo.
- Backup and restore, restoring a pre-routine backup, and Delete all data.
- `Bodyweight` fitting the dial on a narrow screen, in light and dark themes.

## Documentation

Update the Workouts section of the README and replace `docs/images/demo/12-log-strength.png` with a screenshot showing the Routine card.
