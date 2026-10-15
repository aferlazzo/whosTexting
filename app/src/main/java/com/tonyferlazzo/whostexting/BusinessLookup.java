package com.tonyferlazzo.whostexting;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class BusinessLookup {
    static final class Match {
        final String name, evidence, website, source;
        Match(String name, String evidence, String website, String source) {
            this.name = name; this.evidence = evidence; this.website = website; this.source = source;
        }
    }
    static final class Result {
        final List<Match> matches = new ArrayList<>();
        final List<String> unavailable = new ArrayList<>();
    }
    interface Transport { String get(String url) throws Exception; }
    static Result lookup(String number) { return lookup(number, BusinessLookup::download); }
    static Result lookup(String number, Transport transport) {
        Result result = new Result();
        String key = MessagesListener.numberKey(number);
        // Short codes have no globally unique business identity; do not guess one.
        if (key.replaceAll("[^0-9]", "").length() < 7) return result;
        try { result.matches.addAll(parseWikidata(transport.get(wikidataUrl(key)), key)); }
        catch (Exception error) { result.unavailable.add("Wikidata"); }
        try { result.matches.addAll(parseOsm(transport.get(osmUrl(key)), key)); }
        catch (Exception error) { result.unavailable.add("OpenStreetMap"); }
        Set<String> seen = new LinkedHashSet<>();
        result.matches.removeIf(match -> !seen.add(match.name + "|" + match.evidence));
        return result;
    }
    static String wikidataUrl(String key) throws Exception {
        Set<String> variants = new LinkedHashSet<>();
        variants.add(key);
        if (key.startsWith("+1") && key.length() == 12) {
            String n = key.substring(2), a = n.substring(0, 3), b = n.substring(3, 6), c = n.substring(6);
            variants.add("+1-" + a + "-" + b + "-" + c);
            variants.add("+1 " + a + " " + b + " " + c);
            variants.add("+1 " + a + "-" + b + "-" + c);
            variants.add("+1 (" + a + ") " + b + "-" + c);
            variants.add(a + "-" + b + "-" + c);
            variants.add("(" + a + ") " + b + "-" + c);
            variants.add(n);
        }
        StringBuilder values = new StringBuilder();
        for (String variant : variants) values.append('"').append(variant).append("\" ");
        String query = "PREFIX wdt: <http://www.wikidata.org/prop/direct/> "
            + "PREFIX wikibase: <http://wikiba.se/ontology#> PREFIX bd: <http://www.bigdata.com/rdf#> "
            + "PREFIX wd: <http://www.wikidata.org/entity/> "
            + "SELECT DISTINCT ?item ?itemLabel ?phone ?website WHERE { VALUES ?phone { " + values
            + " } ?item wdt:P1329 ?phone . FILTER NOT EXISTS { ?item wdt:P31 wd:Q5 } "
            + "OPTIONAL { ?item wdt:P856 ?website } SERVICE wikibase:label { bd:serviceParam wikibase:language \"en\". } } LIMIT 8";
        return "https://query.wikidata.org/sparql?format=json&query=" + URLEncoder.encode(query, "UTF-8");
    }
    static String osmUrl(String key) throws Exception {
        String digits = key.replaceAll("[^0-9]", "");
        StringBuilder expression = new StringBuilder("^[^0-9]*");
        if (digits.startsWith("1") && digits.length() == 11) {
            expression.append("(1[^0-9]*)?"); digits = digits.substring(1);
        }
        for (int i = 0; i < digits.length(); i++) expression.append(digits.charAt(i)).append("[^0-9]*");
        expression.append('$');
        String query = "[out:json][timeout:20];(nwr[\"phone\"~\"" + expression
            + "\"];nwr[\"contact:phone\"~\"" + expression + "\"];);out tags 8;";
        return "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8");
    }
    static List<Match> parseWikidata(String body, String key) throws Exception {
        List<Match> matches = new ArrayList<>();
        JSONArray rows = new JSONObject(body).getJSONObject("results").getJSONArray("bindings");
        for (int i = 0; i < rows.length() && matches.size() < 8; i++) {
            JSONObject row = rows.getJSONObject(i);
            String name = value(row, "itemLabel"), phone = value(row, "phone"), item = value(row, "item");
            if (!key.equals(MessagesListener.numberKey(phone)) || !validName(name)
                || !item.matches("https?://www\\.wikidata\\.org/entity/Q[0-9]+")) continue;
            matches.add(new Match(name, "https://www.wikidata.org/wiki/" + item.substring(item.lastIndexOf('/') + 1),
                safeWebsite(value(row, "website")), "Wikidata"));
        }
        return matches;
    }
    static List<Match> parseOsm(String body, String key) throws Exception {
        List<Match> matches = new ArrayList<>();
        JSONObject response = new JSONObject(body);
        if (response.has("remark")) throw new IOException("Directory did not complete query");
        JSONArray rows = response.getJSONArray("elements");
        for (int i = 0; i < rows.length() && matches.size() < 8; i++) {
            JSONObject row = rows.getJSONObject(i), tags = row.optJSONObject("tags");
            if (tags == null) continue;
            String phone = tags.optString("phone"), contactPhone = tags.optString("contact:phone");
            if (!key.equals(MessagesListener.numberKey(phone)) && !key.equals(MessagesListener.numberKey(contactPhone))) continue;
            String name = tags.optString("name"), type = row.optString("type");
            if (!validName(name) || !(type.equals("node") || type.equals("way") || type.equals("relation"))) continue;
            long id = row.optLong("id", -1);
            if (id < 1) continue;
            matches.add(new Match(name, "https://www.openstreetmap.org/" + type + "/" + id,
                safeWebsite(tags.optString("website", tags.optString("contact:website"))), "OpenStreetMap"));
        }
        return matches;
    }
    static boolean validName(String name) {
        return !name.trim().isEmpty() && name.length() <= 160 && !name.matches("Q[0-9]+")
            && !name.contains("\n") && !name.contains("\r");
    }
    static String safeWebsite(String input) {
        try {
            URL url = new URL(input);
            return url.getProtocol().equals("https") && !url.getHost().isEmpty()
                && url.getUserInfo() == null ? input : "";
        } catch (Exception error) { return ""; }
    }
    private static String value(JSONObject row, String key) {
        JSONObject value = row.optJSONObject(key);
        return value == null ? "" : value.optString("value");
    }
    private static String download(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(25000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "WhosTexting/1.0 (https://github.com/aferlazzo/whosTexting)");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Directory unavailable");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1024 * 1024) throw new IOException("Directory response too large");
                    output.write(buffer, 0, count);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }
}
