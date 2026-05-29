package com.example.s_master.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class VisionAnalyzer {

    private static final String TAG = "VisionAnalyzer";
    private static final int TIMEOUT = 60000;

    public interface AnalyzeCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public static void analyze(String base64Image, String apiUrl, String apiKey,
                              String model, String systemPrompt, AnalyzeCallback callback) {

        new Thread(() -> {
            try {
                String result = analyzeSync(base64Image, apiUrl, apiKey, model, systemPrompt);
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "Analysis failed", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    private static String analyzeSync(String base64Image, String apiUrl, String apiKey,
                                      String model, String systemPrompt) throws Exception {

        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);

        String json = buildRequestJson(base64Image, model, systemPrompt);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String error = readErrorStream(conn);
            throw new Exception("API Error " + responseCode + ": " + error);
        }

        String response = readStream(conn.getInputStream());
        return parseResponse(response);

    }

    private static String buildRequestJson(String base64Image, String model, String systemPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(escapeJson(model)).append("\",");
        sb.append("\"messages\":[");
        sb.append("{");
        sb.append("\"role\":\"system\",");
        sb.append("\"content\":\"").append(escapeJson(systemPrompt)).append("\"");
        sb.append("},");
        sb.append("{");
        sb.append("\"role\":\"user\",");
        sb.append("\"content\":[");
        sb.append("{");
        sb.append("\"type\":\"text\",");
        sb.append("\"text\":\"请分析这张截图中遇到的问题，并给出建议：\"");
        sb.append("},");
        sb.append("{");
        sb.append("\"type\":\"image_url\",");
        sb.append("\"image_url\":{");
        sb.append("\"url\":\"data:image/jpeg;base64,").append(base64Image).append("\"");
        sb.append("}");
        sb.append("}");
        sb.append("]");
        sb.append("}");
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static String parseResponse(String json) {
        int contentStart = json.indexOf("\"content\":\"");
        if (contentStart == -1) {
            return json;
        }

        contentStart += 10;
        int contentEnd = json.indexOf("\"", contentStart);

        if (contentEnd == -1) {
            return json;
        }

        String content = json.substring(contentStart, contentEnd);
        return unescapeJson(content);
    }

    private static String readStream(java.io.InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private static String readErrorStream(HttpURLConnection conn) {
        try {
            return readStream(conn.getErrorStream());
        } catch (Exception e) {
            return "Unknown error";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                 .replace("\\\\", "\\")
                 .replace("\\n", "\n")
                 .replace("\\r", "\r")
                 .replace("\\t", "\t");
    }
}
