package com.example.s_master;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.Random;

public class ChatMonitorService extends Service {

    private static final String TAG = "ChatMonitorService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "s_master_channel";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private AIService aiService;

    private Random random = new Random();
    private int captureInterval = 6000;
    private long lastAnalysisTime = 0;
    private boolean isAnalyzing = false;
    private boolean isManualMode = true;

    private BroadcastReceiver captureReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.s_master.CAPTURE_NOW".equals(intent.getAction())) {
                takeScreenshot();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        aiService = new AIService(this);

        backgroundThread = new HandlerThread("ChatMonitor");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("就绪"));

        registerReceiver(captureReceiver,
                new IntentFilter("com.example.s_master.CAPTURE_NOW"),
                Context.RECEIVER_NOT_EXPORTED);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "S master",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification createNotification(String status) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Intent analyzeIntent = new Intent("com.example.s_master.CAPTURE_NOW");
        analyzeIntent.setPackage(getPackageName());
        PendingIntent analyzePi = PendingIntent.getBroadcast(this, 0, analyzeIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String contentText;
        if ("分析中".equals(status)) {
            contentText = "⏳ AI 正在分析屏幕内容...";
        } else {
            contentText = "下拉通知栏，点击「开始分析」按钮截图";
        }

        return builder
                .setContentTitle("S master")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_camera, "📷 开始分析", analyzePi)
                .build();
    }

    private void updateNotification(String status) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, createNotification(status));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.hasExtra("mode")) {
                isManualMode = "manual".equals(intent.getStringExtra("mode"));
            }
            if (intent.hasExtra("resultCode") && intent.hasExtra("resultData")) {
                int resultCode = intent.getIntExtra("resultCode", 0);
                Intent data;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    data = intent.getParcelableExtra("resultData", Intent.class);
                } else {
                    data = intent.getParcelableExtra("resultData");
                }
                if (data != null) {
                    setupMediaProjection(resultCode, data);
                }
            }
        }
        return START_STICKY;
    }

    private void setupMediaProjection(int resultCode, Intent data) {
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection != null) {
            setupVirtualDisplay();
            updateNotification("就绪");
            if (!isManualMode) {
                startCaptureLoop();
            }
        }
    }

    private void setupVirtualDisplay() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            if (!isManualMode) {
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    if (!isAnalyzing) {
                        processScreenshot(image);
                    }
                    image.close();
                }
            }
        }, backgroundHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "S master Capture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                backgroundHandler
        );
    }

    private void startCaptureLoop() {
        backgroundHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isManualMode) {
                    takeScreenshot();
                    backgroundHandler.postDelayed(this,
                            captureInterval + random.nextInt(3000));
                }
            }
        }, 3000);
    }

    private void takeScreenshot() {
        if (imageReader == null || isAnalyzing) return;

        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            sendSuggestion("⚠️ 截图失败，请检查权限");
            return;
        }
        processScreenshot(image);
        image.close();
    }

    private void processScreenshot(Image image) {
        long now = System.currentTimeMillis();
        if (!isManualMode && now - lastAnalysisTime < 10000) return;
        lastAnalysisTime = now;

        Bitmap bitmap = imageToBitmap(image);
        if (bitmap == null) return;

        isAnalyzing = true;
        updateNotification("分析中");

        if (aiService.hasApiKey()) {
            sendSuggestion("🤖 AI 正在分析屏幕内容...");

            final Bitmap safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            bitmap.recycle();

            aiService.analyzeScreenshot(safeBitmap, new AIService.AiCallback() {
                @Override
                public void onResult(String analysis, String suggestion) {
                    String msg = (analysis.isEmpty() ? "" : "📊 " + analysis + "\n\n")
                            + "💡 " + suggestion;
                    sendSuggestion(msg);
                    isAnalyzing = false;
                    updateNotification("就绪");
                    if (!safeBitmap.isRecycled()) safeBitmap.recycle();
                }

                @Override
                public void onError(String error) {
                    if ("NO_API_KEY".equals(error)) {
                        sendSuggestion("⚠️ 请先在设置中填写 API Key");
                    } else {
                        Log.e(TAG, "Vision AI error: " + error);
                        sendSuggestion("⚠️ AI 分析失败：" + error);
                    }
                    isAnalyzing = false;
                    updateNotification("就绪");
                    if (!safeBitmap.isRecycled()) safeBitmap.recycle();
                }
            });
        } else {
            sendSuggestion("⚠️ 请先在设置中填写 API Key");
            isAnalyzing = false;
            updateNotification("就绪");
            bitmap.recycle();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        if (image.getPlanes().length == 0) return null;
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride = image.getPlanes()[0].getPixelStride();
        int rowStride = image.getPlanes()[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        if (rowPadding > 0) {
            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            bitmap.recycle();
            return cropped;
        }
        return bitmap;
    }

    private void sendSuggestion(String suggestion) {
        Intent intent = new Intent("com.example.s_master.SUGGESTION");
        intent.putExtra("suggestion", suggestion);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(captureReceiver);
        } catch (Exception e) {}
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();

        if (backgroundThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                backgroundThread.quitSafely();
            } else {
                backgroundThread.quit();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
