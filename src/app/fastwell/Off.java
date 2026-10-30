package app.fastwell;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal Open Food Facts client (search and barcode lookup). Results are delivered on the main thread. */
final class Off {
    private Off() {}

    interface Callback { void done(List<Db.Item> items, String error); }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String FIELDS = "code,product_name,brands,nutriments,serving_quantity";
    private static final String UA = "Fastwell/1.0 (Android; personal food journal)";

    static void search(final String query, final Callback cb) {
        IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // The legacy cgi/search.pl endpoint now returns 503 for anonymous clients; use Search-a-licious.
                    String url = "https://search.openfoodfacts.org/search?page_size=50"
                            + "&fields=" + FIELDS + "&q=" + URLEncoder.encode(query, "UTF-8");
                    JSONObject o = new JSONObject(get(url));
                    JSONArray arr = o.optJSONArray("hits");
                    List<Db.Item> out = new ArrayList<Db.Item>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            Db.Item it = parse(arr.getJSONObject(i));
                            if (it != null) out.add(it);
                        }
                    }
                    post(cb, out, null);
                } catch (Exception e) {
                    post(cb, new ArrayList<Db.Item>(), "Couldn't reach Open Food Facts. Check your connection.");
                }
            }
        });
    }

    static void barcode(final String code, final Callback cb) {
        IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://world.openfoodfacts.org/api/v2/product/" + URLEncoder.encode(code, "UTF-8")
                            + ".json?fields=" + FIELDS;
                    JSONObject o = new JSONObject(get(url));
                    List<Db.Item> out = new ArrayList<Db.Item>();
                    if (o.optInt("status", 0) == 1 && o.has("product")) {
                        JSONObject p = o.getJSONObject("product");
                        if (!p.has("code")) p.put("code", code);
                        Db.Item it = parse(p);
                        if (it != null) out.add(it);
                    }
                    post(cb, out, null);
                } catch (java.io.FileNotFoundException e) {
                    post(cb, new ArrayList<Db.Item>(), null); // 404 = unknown product
                } catch (Exception e) {
                    post(cb, new ArrayList<Db.Item>(), "Couldn't reach Open Food Facts. Check your connection.");
                }
            }
        });
    }

    private static void post(final Callback cb, final List<Db.Item> items, final String err) {
        MAIN.post(new Runnable() {
            @Override
            public void run() { cb.done(items, err); }
        });
    }

    static Db.Item parse(JSONObject p) {
        String name = p.optString("product_name", "").trim();
        JSONObject n = p.optJSONObject("nutriments");
        if (name.isEmpty() || n == null) return null;
        double kcal = n.optDouble("energy-kcal_100g", Double.NaN);
        if (Double.isNaN(kcal)) {
            double kj = n.optDouble("energy_100g", Double.NaN);
            if (Double.isNaN(kj)) return null;
            kcal = kj / 4.184;
        }
        Db.Item it = new Db.Item();
        it.name = name;
        JSONArray brandArr = p.optJSONArray("brands"); // array in search results, comma-separated string in the product API
        String brands = brandArr != null ? brandArr.optString(0, "") : p.optString("brands", "");
        it.brand = brands.contains(",") ? brands.substring(0, brands.indexOf(',')).trim() : brands.trim();
        it.barcode = p.optString("code", "");
        it.kcal100 = kcal;
        it.p100 = zeroIfNaN(n.optDouble("proteins_100g", 0));
        it.c100 = zeroIfNaN(n.optDouble("carbohydrates_100g", 0));
        it.f100 = zeroIfNaN(n.optDouble("fat_100g", 0));
        it.servingG = zeroIfNaN(p.optDouble("serving_quantity", 0));
        return it;
    }

    private static double zeroIfNaN(double v) { return Double.isNaN(v) ? 0 : v; }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setRequestProperty("User-Agent", UA);
        con.setRequestProperty("Accept", "application/json");
        try {
            InputStream is = con.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int k;
            while ((k = r.read(buf)) > 0) sb.append(buf, 0, k);
            r.close();
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }
}
