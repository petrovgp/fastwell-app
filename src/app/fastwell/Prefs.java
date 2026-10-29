package app.fastwell;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

/** Profile, targets and settings (small key-value data; the journals live in SQLite). */
final class Prefs {
    private final SharedPreferences p;

    Prefs(Context c) {
        p = c.getApplicationContext().getSharedPreferences("fastwell", Context.MODE_PRIVATE);
    }

    boolean onboarded() { return p.getBoolean("onboarded", false); }
    void setOnboarded(boolean v) { p.edit().putBoolean("onboarded", v).apply(); }

    int kcalTarget() { return p.getInt("kcal", 2000); }
    int proteinTarget() { return p.getInt("protein", 120); }
    int carbsTarget() { return p.getInt("carbs", 220); }
    int fatTarget() { return p.getInt("fat", 65); }

    void setTargets(int kcal, int protein, int carbs, int fat) {
        p.edit().putInt("kcal", kcal).putInt("protein", protein)
                .putInt("carbs", carbs).putInt("fat", fat).apply();
    }

    String planId() { return p.getString("plan", "16:8"); }
    int customHours() { return p.getInt("custom_hours", 16); }

    void setPlan(String id, int customHours) {
        p.edit().putString("plan", id).putInt("custom_hours", customHours).apply();
    }

    FastingLogic.Plan plan() { return FastingLogic.plan(planId(), customHours()); }

    boolean ongoingNotif() { return p.getBoolean("notif_ongoing", true); }
    boolean goalNotif() { return p.getBoolean("notif_goal", true); }
    void setOngoingNotif(boolean v) { p.edit().putBoolean("notif_ongoing", v).apply(); }
    void setGoalNotif(boolean v) { p.edit().putBoolean("notif_goal", v).apply(); }

    boolean male() { return p.getBoolean("male", true); }
    int age() { return p.getInt("age", 30); }
    int heightCm() { return p.getInt("height", 175); }
    float weightKg() { return p.getFloat("weight", 75f); }
    int activity() { return p.getInt("activity", 1); }

    void setProfile(boolean male, int age, int heightCm, float weightKg, int activity) {
        p.edit().putBoolean("male", male).putInt("age", age).putInt("height", heightCm)
                .putFloat("weight", weightKg).putInt("activity", activity).apply();
    }

    boolean askedNotifPermission() { return p.getBoolean("asked_notif", false); }
    void setAskedNotifPermission() { p.edit().putBoolean("asked_notif", true).apply(); }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            Object v = e.getValue();
            JSONObject typed = new JSONObject();
            if (v instanceof Boolean) typed.put("b", v);
            else if (v instanceof Integer) typed.put("i", v);
            else if (v instanceof Float) typed.put("f", ((Float) v).doubleValue());
            else if (v instanceof Long) typed.put("l", v);
            else if (v instanceof String) typed.put("s", v);
            else continue;
            o.put(e.getKey(), typed);
        }
        return o;
    }

    void fromJson(JSONObject o) throws JSONException {
        SharedPreferences.Editor ed = p.edit().clear();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            JSONObject t = o.getJSONObject(k);
            if (t.has("b")) ed.putBoolean(k, t.getBoolean("b"));
            else if (t.has("i")) ed.putInt(k, t.getInt("i"));
            else if (t.has("f")) ed.putFloat(k, (float) t.getDouble("f"));
            else if (t.has("l")) ed.putLong(k, t.getLong("l"));
            else if (t.has("s")) ed.putString(k, t.getString("s"));
        }
        ed.putBoolean("onboarded", true);
        ed.apply();
    }
}
