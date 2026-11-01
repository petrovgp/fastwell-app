package app.fastwell;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Export to and restore from a user-chosen JSON file. MainActivity starts the pickers and receives their results. */
final class Backup {
    private Backup() {}

    static Intent exportIntent() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "fastwell-backup-" + LocalDate.now() + ".json");
        return i;
    }

    static Intent importIntent() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/json", "text/plain", "application/octet-stream"});
        return i;
    }

    static void exportTo(MainActivity a, Uri uri) {
        try {
            JSONObject o = new JSONObject();
            o.put("app", "fastwell");
            o.put("version", FastingLogic.BACKUP_VERSION);
            o.put("exportedAt", System.currentTimeMillis());
            o.put("prefs", a.prefs.toJson());
            o.put("tables", a.db.exportTables());
            OutputStream os = a.getContentResolver().openOutputStream(uri, "wt");
            try {
                os.write(o.toString(1).getBytes(StandardCharsets.UTF_8));
            } finally {
                os.close();
            }
            a.snack("Backup saved", null);
        } catch (Exception e) {
            Ui.toast(a, "Export failed: " + e.getMessage());
        }
    }

    static void importFrom(final MainActivity a, Uri uri) {
        final JSONObject o;
        try {
            InputStream is = a.getContentResolver().openInputStream(uri);
            StringBuilder sb = new StringBuilder();
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            } finally {
                is.close();
            }
            o = new JSONObject(sb.toString());
            if (!"fastwell".equals(o.optString("app"))) throw new IllegalArgumentException("Not a Fastwell backup");
            String problem = FastingLogic.backupProblem(o.optInt("version"), Db.tablesIn(o.optJSONObject("tables")));
            if (problem != null) throw new IllegalArgumentException(problem);
        } catch (Exception e) {
            Ui.toast(a, "Could not read backup: " + e.getMessage());
            return;
        }
        new AlertDialog.Builder(a)
                .setTitle("Replace all data?")
                .setMessage("Importing replaces every fast, meal, workout and routine on this phone with the backup's contents.")
                .setPositiveButton("Replace", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            a.db.importTables(o.getJSONObject("tables"));
                            if (o.has("prefs")) a.prefs.fromJson(o.getJSONObject("prefs"));
                            Notifier.refresh(a);
                            a.showTab(MainActivity.TAB_TODAY);
                            a.snack("Backup restored", null);
                        } catch (Exception e) {
                            Ui.toast(a, "Import failed: " + e.getMessage());
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
