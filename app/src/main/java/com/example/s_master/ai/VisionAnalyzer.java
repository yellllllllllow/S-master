package com.example.s_master.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class VisionAnalyzer {

    private static final String TAG = "VisionAnalyzer";
    private static final int TIMEOUT = 60000;
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 1000;

    public interface AnalyzeCallback {
        void onSuccess(String result);
        void onError(String error);
        void onProgress(int progress);
    }

    public static void analyze(String base64Image, String apiUrl, String apiKey,
                              String model, String systemPrompt, AnalyzeCallback callback) {
        analyzeWithRetry(base64Image, apiUrl, apiKey, model, systemPrompt, callback, 0);
    }

    private static void analyzeWithRetry(String base64Image, String apiUrl, String apiKey,
                                         String model, String systemPrompt, 
                                         AnalyzeCallback callback, int retryCount) {

        new Thread(() -> {
            try {
                notifyProgress(callback, 20);
                String result = analyzeSync(base64Image, apiUrl, apiKey, model, systemPrompt);
                notifyProgress(callback, 100);
                notifySuccess(callback, result);
            } catch (Exception e) {
                Log.e(TAG, "Analysis failed (attempt " + (retryCount + 1) + ")", e);
                
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    analyzeWithRetry(base64Image, apiUrl, apiKey, model, systemPrompt, callback, retryCount + 1);
                } else {
                    notifyError(callback, e.getMessage());
                }
            }
        }).start();
    }

    private static String analyzeSync(String base64Image, String apiUrl, String apiKey,
                                      String model, String systemPrompt) throws Exception {

        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setUseCaches(false);

            String json = buildRequestJson(base64Image, model, systemPrompt);
            Log.d(TAG, "Request JSON length: " + json.length());

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            
            if (responseCode == 429) {
                throw new Exception("请求过于频繁，请稍后再试");
            }
            
            if (responseCode == 401) {
                throw new Exception("API Key 无效，请检查配置");
            }
            
            if (responseCode == 404) {
                throw new Exception("API 地址不正确");
            }
            
            if (responseCode != 200) {
                String error = readErrorStream(conn);
                throw new Exception("服务器错误 " + responseCode + ": " + parseErrorResponse(error));
            }

            String response = readStream(conn.getInputStream());
            return parseResponse(response);
            
        } finally {
            conn.disconnect();
        }
    }

    private static String buildRequestJson(String base64Image, String model, String systemPrompt) {
        try {
            JSONObject request = new JSONObject();
            request.put("model", model.isEmpty() ? "gpt-4o-mini" : model);
            
            JSONArray messages = new JSONArray();
            
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.put(systemMsg);
            
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            
            JSONArray content = new JSONArray();
            
            JSONObject textContent = new JSONObject();
            textContent.put("type", "text");
            textContent.put("text", "请分析这张截图中遇到的问题，并给出建议：");
            content.put(textContent);
            
            JSONObject imageContent = new JSONObject();
            imageContent.put("type", "image_url");
            
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
            imageContent.put("image_url", imageUrl);
            content.put(imageContent);
            
            userMsg.put("content", content);
            messages.put(userMsg);
            
            request.put("messages", messages);
            request.put("max_tokens", 4096);
            
            return request.toString();
            
        } catch (JSONException e) {
            Log.e(TAG, "JSON build error", e);
            throw new RuntimeException("JSON build error", e);
        }
    }

    private static String parseResponse(String json) {
        try {
            JSONObject response = new JSONObject(json);
            JSONArray choices = response.getJSONArray("choices");
            
            if (choices.length() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                
                if (message.has("content")) {
                    return message.getString("content");
                }
            }
            
            return json;
            
        } catch (JSONException e) {
            Log.e(TAG, "JSON parse error", e);
            return json;
        }
    }

    private static String parseErrorResponse(String json) {
        try {
            JSONObject error = new JSONObject(json);
            if (error.has("error")) {
                JSONObject errorObj = error.getJSONObject("error");
                if (errorObj.has("message")) {
                    return errorObj.getString("message");
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error response parse error", e);
        }
        return json;
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

    private static void notifyProgress(AnalyzeCallback callback, int progress) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(progress));
        }
    }

    private static void notifySuccess(AnalyzeCallback callback, String result) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(result));
        }
    }

    private static void notifyError(AnalyzeCallback callback, String error) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
        }
    }
}
