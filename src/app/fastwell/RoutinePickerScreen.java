package app.fastwell;

import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Lists saved and recent routines, either to pick one for a log or (pick == null) to manage them. */
final class RoutinePickerScreen extends Screen {
    interface Pick {
        void on(Db.Routine r);

        void newRoutine();
    }

    private final Pick pick;

    RoutinePickerScreen(MainActivity a, Pick pick) {
        super(a);
        this.pick = pick;
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

        LinearLayout col = Ui.column(c, 16);
        col.addView(Ui.title(c, pick == null ? "Routines" : "Choose routine"));
        List<Db.Routine> saved = db.routines(true);
        List<Db.Routine> recent = db.routines(false);
        if (recent.size() > FastingLogic.RECENT_ROUTINES) recent = new ArrayList<Db.Routine>(recent.subList(0, FastingLogic.RECENT_ROUTINES));
        if (saved.isEmpty() && recent.isEmpty()) {
            col.addView(Ui.muted(c, "Routines appear here once you log or save one."));
        }
        if (!saved.isEmpty()) {
            col.addView(Ui.sectionTitle(c, "Saved"));
            for (Db.Routine r : saved) col.addView(row(r, r.name, FastingLogic.routineSummary(r.lifts)));
        }
        if (!recent.isEmpty()) {
            col.addView(Ui.sectionTitle(c, "Recent"));
            for (Db.Routine r : recent) {
                int n = FastingLogic.routineSets(r.lifts);
                col.addView(row(r, FastingLogic.routineTitle(r.lifts),
                        "Used " + Ui.shortDate(r.lastUsed) + "  -  " + n + (n == 1 ? " set" : " sets")));
            }
        }
        root.addView(Ui.scroll(c, col), Ui.lp(Ui.MATCH, 0, 1f));

        Button add = Ui.primary(c, "New routine", R.color.train);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { newRoutine(); }
        });
        LinearLayout foot = Ui.vbox(c);
        foot.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 16));
        foot.addView(add, Ui.lp(Ui.MATCH, Ui.dp(c, 56)));
        root.addView(foot, Ui.lp(Ui.MATCH, Ui.WRAP));
        return root;
    }

    private View row(final Db.Routine r, String title, String detail) {
        LinearLayout card = Ui.card(c);
        LinearLayout line = Ui.hbox(c);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout mid = Ui.vbox(c);
        mid.addView(Ui.text(c, title, 16, R.color.text, true));
        mid.addView(Ui.muted(c, detail));
        line.addView(mid, Ui.lp(0, Ui.WRAP, 1f));
        final TextView more = Ui.text(c, "⋮", 22, R.color.text2, true);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("More options");
        Ui.clickable(more, 22);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { menu(more, r); }
        });
        line.addView(more, Ui.lp(Ui.dp(c, 44), Ui.dp(c, 44)));
        card.addView(line);
        // The menu button is a separate clickable child, so a tap on it never reaches the card.
        Ui.clickable(card, 16);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pick == null) edit(r);
                else {
                    a.pop();
                    pick.on(r);
                }
            }
        });
        return card;
    }

    private void menu(View anchor, final Db.Routine r) {
        PopupMenu m = new PopupMenu(c, anchor);
        m.getMenu().add(r.named() ? "Rename" : "Name it");
        m.getMenu().add("Edit");
        m.getMenu().add("Delete");
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String t = item.getTitle().toString();
                if (t.equals("Edit")) edit(r);
                else if (t.equals("Delete")) delete(r);
                else RoutineDialogs.rename(a, r, new Runnable() {
                    @Override
                    public void run() { a.refresh(); }
                });
                return true;
            }
        });
        m.show();
    }

    private void edit(final Db.Routine r) {
        a.push(new RoutineEditScreen(a, "Edit routine", r.lifts, 0, true, new RoutineEditScreen.Done() {
            @Override
            public void on(List<FastingLogic.Lift> lifts) {
                db.updateRoutine(r.id, lifts);
                a.pop();
            }
        }));
    }

    private void delete(final Db.Routine r) {
        // Fetched fresh so undo restores the stored lifts exactly.
        final Db.Routine full = db.getRoutine(r.id);
        if (full == null) return;
        db.deleteRoutine(full.id);
        a.refresh();
        a.snack("Routine deleted", new Runnable() {
            @Override
            public void run() { db.restoreRoutine(full); }
        });
    }

    private void newRoutine() {
        if (pick != null) {
            a.pop();
            pick.newRoutine();
            return;
        }
        a.push(new RoutineEditScreen(a, "New routine", new ArrayList<FastingLogic.Lift>(), 0, true, new RoutineEditScreen.Done() {
            @Override
            public void on(final List<FastingLogic.Lift> lifts) {
                RoutineDialogs.saveAs(a, lifts, new RoutineDialogs.Saved() {
                    @Override
                    public void on(long id, String name) { a.pop(); }
                });
            }
        }));
    }
}
