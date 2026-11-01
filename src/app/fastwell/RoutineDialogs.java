package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Name dialogs for routines: Save as, Name it and Rename. */
final class RoutineDialogs {
    private RoutineDialogs() {}

    interface Saved { void on(long id, String name); }

    /** Asks for a name and stores the lifts as a saved routine, confirming before it replaces one. */
    static void saveAs(final MainActivity a, final List<FastingLogic.Lift> lifts, final Saved then) {
        final EditText name = Ui.field(a, "Routine name", Ui.TXT);
        LinearLayout body = Ui.dialogBody(a);
        body.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP));
        final AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle("Save as")
                .setView(body)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();
        final Button save = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setTextColor(Ui.col(a, R.color.train));
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Ui.col(a, R.color.train));
        save.setEnabled(false);
        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int n) {}

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) { save.setEnabled(!s.toString().trim().isEmpty()); }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String n = name.getText().toString().trim();
                if (n.isEmpty()) return;
                dlg.dismiss();
                if (a.db.namedRoutine(n) == null) {
                    store(a, n, lifts, then);
                    return;
                }
                new AlertDialog.Builder(a)
                        .setTitle("Replace " + n + "?")
                        .setMessage("A routine with this name already exists.")
                        .setPositiveButton("Replace", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) { store(a, n, lifts, then); }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private static void store(MainActivity a, String name, List<FastingLogic.Lift> lifts, Saved then) {
        long id = a.db.saveRoutine(name, lifts);
        then.on(id, name);
    }

    /** Names an unnamed routine or renames a saved one; a clash with another routine keeps the dialog open. */
    static void rename(final MainActivity a, final Db.Routine r, final Runnable then) {
        final EditText name = Ui.field(a, "Routine name", Ui.TXT);
        name.setText(r.name);
        final TextView error = Ui.text(a, "That name is already used", 13, R.color.error, false);
        error.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), 0, 0);
        error.setVisibility(View.GONE);
        LinearLayout body = Ui.dialogBody(a);
        body.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP));
        body.addView(error);
        final AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle(r.named() ? "Rename" : "Name it")
                .setView(body)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();
        final Button save = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setTextColor(Ui.col(a, R.color.train));
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Ui.col(a, R.color.train));
        save.setEnabled(!name.getText().toString().trim().isEmpty());
        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int n) {}

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                error.setVisibility(View.GONE);
                save.setEnabled(!s.toString().trim().isEmpty());
            }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) return;
                Db.Routine clash = a.db.namedRoutine(n);
                if (clash != null && clash.id != r.id) {
                    error.setVisibility(View.VISIBLE);
                    return;
                }
                a.db.renameRoutine(r.id, n);
                dlg.dismiss();
                then.run();
            }
        });
    }
}
