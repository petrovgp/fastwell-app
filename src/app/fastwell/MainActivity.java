package app.fastwell;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;

public class MainActivity extends Activity {
    static final String EXTRA_TAB = "tab";
    static final int TAB_TODAY = 0, TAB_FAST = 1, TAB_FOOD = 2, TAB_WORKOUTS = 3, TAB_PROGRESS = 4;
    private static final String[] TAB_LABELS = {"Today", "Fast", "Food", "Workouts", "Progress"};
    private static final int[] TAB_ICONS = {R.drawable.ic_home, R.drawable.ic_timer, R.drawable.ic_food,
            R.drawable.ic_workout, R.drawable.ic_chart};
    private static final int REQ_EXPORT = 11, REQ_IMPORT = 12, REQ_NOTIF = 13;

    private static WeakReference<MainActivity> current;

    Db db;
    Prefs prefs;
    /** Day shown by the Food and Workouts journals; shared so switching tabs keeps the same day. */
    LocalDate selectedDay = LocalDate.now();
    /** Direction of the day change behind the current rebuild (-1 earlier, 1 later, 0 none), for slide animations. */
    int daySlide;

    private LinearLayout column;
    private FrameLayout content;
    private LinearLayout nav;
    private LinearLayout snack;
    private final ArrayDeque<Screen> stack = new ArrayDeque<Screen>();
    private Screen screen;
    private int tab = TAB_TODAY;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Object backCallback;
    private int insetTop, insetBottom, insetLeft, insetRight, imeBottom;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (screen != null) screen.tick();
            handler.postDelayed(this, 1000);
        }
    };

    /** Leaves the launch screen: onboarding (first run) or Today drops in from the top. */
    private final Runnable afterLaunch = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || !(screen instanceof LaunchScreen)) return;
            if (!prefs.onboarded()) {
                slideIn(new OnboardingScreen(MainActivity.this));
                CrashLog.showIfAny(MainActivity.this);
            } else {
                tab = TAB_TODAY;
                stack.clear();
                slideIn(createTab(TAB_TODAY));
                if (!CrashLog.showIfAny(MainActivity.this)) maybeAskNotificationPermission();
            }
        }
    };

    private final Runnable hideSnack = new Runnable() {
        @Override
        public void run() { snack.setVisibility(View.GONE); }
    };

    static void notifyDataChanged() {
        final MainActivity a = current == null ? null : current.get();
        if (a == null) return;
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() { if (!a.isFinishing()) a.refresh(); }
        });
    }

    // ---------- lifecycle ----------

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        db = Db.get(this);
        prefs = new Prefs(this);
        Notifier.ensureChannels(this);
        setupWindow();
        buildShell();

        boolean coldStart = saved == null && !getIntent().hasExtra(EXTRA_TAB);
        if (coldStart) {
            setScreen(new LaunchScreen(this));
            handler.postDelayed(afterLaunch, LaunchScreen.DURATION_MS);
        } else if (!prefs.onboarded()) {
            setScreen(new OnboardingScreen(this));
        } else {
            int t = saved != null ? saved.getInt("tab", TAB_TODAY) : getIntent().getIntExtra(EXTRA_TAB, TAB_TODAY);
            showTab(t);
            long workoutId = saved != null ? saved.getLong("workout", 0) : 0;
            if (workoutId > 0 && db.getWorkout(workoutId) != null) push(new LogWorkoutScreen(this, db.getWorkout(workoutId)));
            if (saved != null && saved.containsKey("day")) selectedDay = LocalDate.parse(saved.getString("day"));
        }
        if (!(screen instanceof LaunchScreen)) CrashLog.showIfAny(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (prefs.onboarded() && intent.hasExtra(EXTRA_TAB)) showTab(intent.getIntExtra(EXTRA_TAB, TAB_TODAY));
    }

    @Override
    protected void onResume() {
        super.onResume();
        current = new WeakReference<MainActivity>(this);
        Notifier.refresh(this);
        refresh();
        handler.post(ticker);
        maybeAskNotificationPermission();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(afterLaunch);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putString("day", selectedDay.toString());
        if (screen instanceof LogWorkoutScreen) out.putLong("workout", ((LogWorkoutScreen) screen).workoutId());
    }

    // ---------- window and shell ----------

    @SuppressWarnings("deprecation")
    private void setupWindow() {
        getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        applyBars(!Ui.night(this));
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    /** Dark system-bar icons on light backgrounds, light icons on dark ones. */
    @SuppressWarnings("deprecation")
    private void applyBars(boolean lightBackground) {
        // The decor view must exist first: PhoneWindow.getInsetsController() dereferences it,
        // and calling it before the decor is installed crashes on launch (API 30+).
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController ctl = decor.getWindowInsetsController();
            if (ctl == null) ctl = getWindow().getInsetsController();
            if (ctl != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ctl.setSystemBarsAppearance(lightBackground ? light : 0, light);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (lightBackground) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        }
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.col(this, R.color.bg));

        column = Ui.vbox(this);
        FrameLayout host = new FrameLayout(this);
        content = new FrameLayout(this);
        host.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        snack = Ui.hbox(this);
        snack.setVisibility(View.GONE);
        snack.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        snack.setMinimumHeight(Ui.dp(this, 48));
        snack.setBackground(Ui.round(Ui.night(this) ? 0xFFE6E9F7 : 0xFF1A2455, Ui.dp(this, 12)));
        snack.setElevation(Ui.dp(this, 8));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        sp.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 12));
        host.addView(snack, sp);

        column.addView(host, Ui.lp(Ui.MATCH, 0, 1f));
        nav = Ui.hbox(this);
        nav.setBackgroundColor(Ui.col(this, R.color.surface));
        nav.setElevation(Ui.dp(this, 2));
        column.addView(nav, Ui.lp(Ui.MATCH, Ui.WRAP));
        root.addView(column, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        setContentView(root);

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                readInsets(insets);
                applyInsets();
                return insets;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void readInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            insetTop = bars.top;
            insetBottom = bars.bottom;
            insetLeft = bars.left;
            insetRight = bars.right;
            imeBottom = ime.bottom;
        } else {
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            insetLeft = insets.getSystemWindowInsetLeft();
            insetRight = insets.getSystemWindowInsetRight();
            imeBottom = 0;
        }
    }

    private void applyInsets() {
        boolean keyboard = imeBottom > insetBottom;
        boolean showNav = screen != null && screen.showsNav() && !keyboard;
        nav.setVisibility(showNav ? View.VISIBLE : View.GONE);
        nav.setPadding(0, 0, 0, insetBottom);
        int bottom = keyboard ? imeBottom : (showNav ? 0 : insetBottom);
        column.setPadding(insetLeft, insetTop, insetRight, bottom);
    }

    private void buildNav() {
        nav.removeAllViews();
        int selected = screen == null ? -1 : (screen.navTab() >= 0 ? screen.navTab() : tab);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int t = i;
            boolean on = i == selected;
            LinearLayout item = Ui.vbox(this);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            FrameLayout pill = new FrameLayout(this);
            if (on) pill.setBackground(Ui.round(Ui.alpha(Ui.col(this, R.color.primary), Ui.night(this) ? 0.24f : 0.14f), Ui.dp(this, 16)));
            ImageView icon = Ui.icon(this, TAB_ICONS[i], on ? R.color.primary : R.color.text2, 24);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24), Gravity.CENTER);
            pill.addView(icon, ip);
            item.addView(pill, Ui.lp(Ui.dp(this, 60), Ui.dp(this, 32)));
            TextView label = Ui.text(this, TAB_LABELS[i], 12, on ? R.color.text : R.color.text2, on);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setPadding(0, Ui.dp(this, 4), 0, 0);
            item.addView(label);
            item.setContentDescription(TAB_LABELS[i]);
            // The press ripple lives on the pill so it lands where the selection bubble does.
            Ui.clickable(pill, 16);
            pill.setClickable(false);
            pill.setFocusable(false);
            pill.setDuplicateParentStateEnabled(true);
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { showTab(t); }
            });
            nav.addView(item, Ui.lp(0, Ui.WRAP, 1f));
        }
    }

    // ---------- navigation ----------

    void showTab(int t) {
        tab = Math.max(0, Math.min(4, t));
        stack.clear();
        setScreen(createTab(tab));
    }

    private Screen createTab(int t) {
        switch (t) {
            case TAB_FAST: return new FastScreen(this);
            case TAB_FOOD: return new FoodScreen(this);
            case TAB_WORKOUTS: return new WorkoutsScreen(this);
            case TAB_PROGRESS: return new ProgressScreen(this);
            default: return new TodayScreen(this);
        }
    }

    Screen currentScreen() { return screen; }

    void push(Screen s) {
        if (screen != null) stack.push(screen);
        setScreen(s);
    }

    void pop() {
        if (stack.isEmpty()) return;
        setScreen(stack.pop());
    }

    void finishOnboarding() {
        showTab(TAB_TODAY);
        maybeAskNotificationPermission();
    }

    private void setScreen(Screen s) {
        screen = s;
        render(false);
        updateBack();
    }

    /** Rebuilds the current screen from the database, keeping the scroll position. */
    void refresh() {
        if (screen != null) render(true);
    }

    /** Rebuilds the current screen scrolled to the top. */
    void rebuild() {
        if (screen != null) render(false);
        updateBack();
    }

    /** Shows a screen that slides in from the left over the current one. */
    private void slideIn(Screen s) {
        screen = s;
        final View old = content.getChildCount() > 0 ? content.getChildAt(content.getChildCount() - 1) : null;
        final View v = s.build();
        if (v.getBackground() == null) v.setBackgroundColor(Ui.col(this, R.color.bg));
        float w = Math.max(content.getWidth(), getWindow().getDecorView().getWidth());
        // Park the old screen behind the shell at its current size, so showing the
        // nav bar doesn't squash it while the new screen slides over.
        final FrameLayout root = (FrameLayout) column.getParent();
        if (old != null) {
            int[] at = new int[2], base = new int[2];
            old.getLocationInWindow(at);
            root.getLocationInWindow(base);
            FrameLayout.LayoutParams op = new FrameLayout.LayoutParams(old.getWidth(), old.getHeight());
            op.leftMargin = at[0] - base[0];
            op.topMargin = at[1] - base[1];
            content.removeView(old);
            root.addView(old, 0, op);
        }
        v.setTranslationX(-w);
        content.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        v.setElevation(Ui.dp(this, 4));
        buildNav();
        applyInsets();
        applyBars(s.lightBars());
        s.tick();
        updateBack();
        if (old != null) old.animate().translationX(w * 0.18f).alpha(0.4f).setDuration(650).start();
        nav.setTranslationX(-w);
        nav.animate().translationX(0).setDuration(650)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2.2f)).start();
        v.animate().translationX(0).setDuration(650)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2.2f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.setElevation(0);
                        if (old != null) root.removeView(old);
                    }
                }).start();
    }

    private void render(boolean keepScroll) {
        int y = 0;
        if (keepScroll) {
            ScrollView old = findScroll(content);
            if (old != null) y = old.getScrollY();
        }
        content.removeAllViews();
        View v = screen.build();
        daySlide = 0;
        content.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        if (y > 0) {
            final ScrollView sv = findScroll(v);
            final int fy = y;
            if (sv != null) sv.post(new Runnable() {
                @Override
                public void run() { sv.scrollTo(0, fy); }
            });
        }
        buildNav();
        applyInsets();
        applyBars(screen.lightBars());
        screen.tick();
    }

    private static ScrollView findScroll(View v) {
        if (v instanceof ScrollView && "main_scroll".equals(v.getTag())) return (ScrollView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                ScrollView s = findScroll(g.getChildAt(i));
                if (s != null) return s;
            }
        }
        return null;
    }

    private boolean canHandleBack() {
        if (screen instanceof LaunchScreen) return false;
        if (screen instanceof OnboardingScreen) return ((OnboardingScreen) screen).canGoBack();
        if (!stack.isEmpty()) return true;
        return prefs.onboarded() && tab != TAB_TODAY && !(screen instanceof OnboardingScreen);
    }

    void handleBack() {
        if (screen instanceof OnboardingScreen) {
            ((OnboardingScreen) screen).back();
            return;
        }
        if (!stack.isEmpty()) pop();
        else if (tab != TAB_TODAY) showTab(TAB_TODAY);
    }

    void updateBack() {
        if (Build.VERSION.SDK_INT < 33) return;
        boolean need = canHandleBack();
        if (need && backCallback == null) {
            backCallback = Back33.register(this, new Runnable() {
                @Override
                public void run() { handleBack(); }
            });
        } else if (!need && backCallback != null) {
            Back33.unregister(this, backCallback);
            backCallback = null;
        }
    }

    /** Used on Android 15 and older (and when predictive back is not active). */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (canHandleBack()) handleBack();
        else super.onBackPressed();
    }

    // ---------- shared actions ----------

    void snack(String message, final Runnable undo) {
        snack.removeAllViews();
        int fg = Ui.night(this) ? 0xFF0F1A44 : 0xFFF2F4FF;
        TextView t = Ui.text(this, message, 14, R.color.text, false);
        t.setTextColor(fg);
        snack.addView(t, Ui.lp(0, Ui.WRAP, 1f));
        if (undo != null) {
            TextView u = Ui.text(this, "Undo", 14, R.color.primary, true);
            u.setTextColor(Ui.night(this) ? 0xFF2E5BFF : 0xFFFFC48A);
            u.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            Ui.clickable(u, 8);
            u.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snack.setVisibility(View.GONE);
                    undo.run();
                    Notifier.refresh(MainActivity.this);
                    refresh();
                }
            });
            snack.addView(u);
        }
        snack.setVisibility(View.VISIBLE);
        snack.bringToFront();
        handler.removeCallbacks(hideSnack);
        handler.postDelayed(hideSnack, undo != null ? 5000 : 3000);
    }

    void startFast() {
        FastingLogic.Plan p = prefs.plan();
        final long id = db.startFast(System.currentTimeMillis(), p.goalMinutes(), p.id);
        Notifier.refresh(this);
        refresh();
        snack("Fast started - goal " + p.fastHours + "h", new Runnable() {
            @Override
            public void run() { db.deleteFast(id); }
        });
    }

    /** Removes the journals, settings and crash report, then starts over from onboarding. */
    void deleteAllData() {
        db.deleteAll();
        prefs.clear();
        CrashLog.clear(this);
        Notifier.refresh(this);
        selectedDay = LocalDate.now();
        tab = TAB_TODAY;
        stack.clear();
        setScreen(new OnboardingScreen(this));
        snack("All data deleted", null);
    }

    /** "Now" if the selected journal day is today, otherwise that day at the current clock time. */
    long timeOnSelectedDay() {
        if (selectedDay.equals(LocalDate.now())) return System.currentTimeMillis();
        return LocalDateTime.of(selectedDay, LocalTime.now().withSecond(0).withNano(0))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** Returns from the day picker to the journal it was opened from, showing day d. */
    void pickDay(LocalDate d) {
        daySlide = Integer.signum(d.compareTo(selectedDay));
        selectedDay = d;
        pop();
    }

    void goToDay(LocalDate d) {
        daySlide = Integer.signum(d.compareTo(selectedDay));
        selectedDay = d;
        refresh();
    }

    // ---------- notifications permission ----------

    private void maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || !prefs.onboarded() || prefs.askedNotifPermission()) return;
        if (screen instanceof LaunchScreen) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        prefs.setAskedNotifPermission();
        new AlertDialog.Builder(this)
                .setTitle("Allow notifications?")
                .setMessage("So Fastwell can show your running fast and tell you when you reach your goal.")
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                    }
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_NOTIF) Notifier.refresh(this);
    }

    // ---------- backup ----------

    @SuppressWarnings("deprecation")
    void startExport() {
        startActivityForResult(Backup.exportIntent(), REQ_EXPORT);
    }

    @SuppressWarnings("deprecation")
    void startImport() {
        startActivityForResult(Backup.importIntent(), REQ_IMPORT);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_EXPORT) Backup.exportTo(this, uri);
        else if (req == REQ_IMPORT) Backup.importFrom(this, uri);
    }
}
