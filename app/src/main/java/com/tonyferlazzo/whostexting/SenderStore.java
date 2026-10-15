package com.tonyferlazzo.whostexting;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class SenderStore {
    static final int LIMIT = 30;
    static final class Sender {
        final String id, label, number;
        final long time;
        Sender(String label, String number, long time) {
            this.label = label;
            this.number = number;
            this.id = number.isEmpty() ? "label:" + label : number;
            this.time = time;
        }
    }
    static synchronized List<Sender> read(Context context) {
        List<Sender> result = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(context.getSharedPreferences("captured_senders", Context.MODE_PRIVATE)
                .getString("recent", "[]"));
            for (int i = 0; i < data.length() && result.size() < LIMIT; i++) {
                JSONObject row = data.getJSONObject(i);
                result.add(new Sender(row.getString("label"), row.getString("number"), row.getLong("time")));
            }
        } catch (Exception ignored) { /* Corrupt local history is not an identity source. */ }
        return result;
    }
    static synchronized void record(Context context, String label, String number) {
        List<Sender> history = read(context);
        Sender latest = new Sender(label, number, System.currentTimeMillis());
        history.removeIf(row -> row.id.equals(latest.id));
        history.add(0, latest);
        JSONArray data = new JSONArray();
        for (int i = 0; i < history.size() && i < LIMIT; i++) {
            Sender row = history.get(i);
            JSONObject json = new JSONObject();
            try { json.put("label", row.label).put("number", row.number).put("time", row.time); }
            catch (Exception impossible) { continue; }
            data.put(json);
        }
        context.getSharedPreferences("captured_senders", Context.MODE_PRIVATE)
            .edit().putString("recent", data.toString()).apply();
    }
    static void clear(Context context) {
        context.getSharedPreferences("captured_senders", Context.MODE_PRIVATE).edit().clear().apply();
    }
}
