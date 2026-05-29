package com.example.s_master;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.content.IntentFilter;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.s_master.ai.VisionAnalyzer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingService extends Service {

    private static final String TAG = "FloatingService";
    private static final String CHANNEL_ID = "S_master_channel";
    private static final String CHANNEL_NAME = "S-master Service";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private View floatingView;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ExecutorService executor;

    private WindowManager.LayoutParams params;
    private boolean isDragging = false;
    private float touchStartX, touchStartY;
    private int initialX, initialY;

    private volatile boolean isAnalyzing = false;
    private MediaProjection.Callback projectionCallback;

    private static final String ACTION_STOP = "com.example.s_master.STOP";
    private static final String ACTION_OPEN = "com.example.s_master.OPEN";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        initFloatingButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP.equals(intent.getAction())) {
                stopSelf();
                return START_STICKY;
            }

            if (intent.hasExtra("resultCode") && intent.hasExtra("resultData")) {
                int resultCode = intent.getIntExtra("resultCode", -1);
                Intent data = intent.getParcelableExtra("resultData");
                if (resultCode != -1 && data != null) {
                    setupMediaProjection(resultCode, data);
                }
            }
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STOP);
        filter.addAction(ACTION_OPEN);
        registerReceiver(receiver, filter);

        return START_STICKY;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("S-master 悬浮窗服务");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification createNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setAction(ACTION_OPEN);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, FloatingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("S-master")
                .setContentText("悬浮球已就绪，点击截屏分析")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(Notification.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(openPending)
                .addAction(R.drawable.ic_notification, "停止", stopPending)
                .build();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initFloatingButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = View.inflate(this, R.layout.floating_button, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;

        ImageView btn = floatingView.findViewById(R.id.floatingBtn);
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging = false;
                    touchStartX = event.getRawX();
                    touchStartY = event.getRawY();
                    initialX = params.x;
                    initialY = params.y;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - touchStartX;
                    float deltaY = event.getRawY() - touchStartY;

                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        params.x = initialX + (int) deltaX;
                        params.y = initialY + (int) deltaY;
                        windowManager.updateViewLayout(floatingView, params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        onFloatingClick();
                    }
                    return true;
            }
            return false;
        });

        windowManager.addView(floatingView, params);
    }

    private void onFloatingClick() {
        if (isAnalyzing) {
            Toast.makeText(this, "正在分析中...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mediaProjection == null) {
            Toast.makeText(this, "屏幕投影未就绪，请重启服务", Toast.LENGTH_SHORT).show();
            return;
        }

        takeScreenshot();
    }

    private void setupMediaProjection(int resultCode, Intent data) {
        try {
            MediaProjectionManager pm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = pm.getMediaProjection(resultCode, data);

            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null");
                return;
            }

            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.w(TAG, "MediaProjection stopped by system");
                    stopSelf();
                }
            };
            mediaProjection.registerCallback(projectionCallback, null);

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int density = metrics.densityDpi;

            backgroundThread = new HandlerThread("ScreenshotWorker");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "S_master_capture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    backgroundHandler
            );

            imageReader.setOnImageAvailableListener(reader -> {
                if (!isAnalyzing) {
                    isAnalyzing = true;
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image != null) {
                            Bitmap bitmap = imageToBitmap(image);
                            if (bitmap != null) {
                                analyzeImage(bitmap);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Screenshot error", e);
                        isAnalyzing = false;
                    } finally {
                        if (image != null) {
                            try { image.close(); } catch (Exception ignored) {}
                        }
                    }
                }
            }, backgroundHandler);

            Log.d(TAG, "MediaProjection setup complete");

        } catch (Exception e) {
            Log.e(TAG, "Failed to setup MediaProjection", e);
            Toast.makeText(this, "屏幕投影初始化失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int width = image.getWidth();
            int height = image.getHeight();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Image to bitmap error", e);
            return null;
        }
    }

    private void takeScreenshot() {
        if (imageReader == null) {
            Toast.makeText(this, "截图服务未就绪", Toast.LENGTH_SHORT).show();
            isAnalyzing = false;
            return;
        }

        Toast.makeText(this, "正在截取屏幕...", Toast.LENGTH_SHORT).show();
    }

    private void analyzeImage(Bitmap bitmap) {
        executor.execute(() -> {
            try {
                updateNotification("正在分析截屏...");

                String base64Image = bitmapToBase64(bitmap);

                VisionAnalyzer.analyze(
                        base64Image,
                        MainActivity.currentApiUrl,
                        MainActivity.currentApiKey,
                        MainActivity.currentModel,
                        MainActivity.currentPrompt,
                        new VisionAnalyzer.AnalyzeCallback() {
                            @Override
                            public void onSuccess(String result) {
                                isAnalyzing = false;

                                if (MainActivity.isSilentMode) {
                                    copyToClipboard(result);
                                    updateNotification("分析完成，已复制到剪贴板");
                                } else {
                                    showResultNotification(result);
                                }

                                bitmap.recycle();
                            }

                            @Override
                            public void onError(String error) {
                                isAnalyzing = false;
                                updateNotification("分析失败: " + error);
                                Toast.makeText(FloatingService.this, "分析失败: " + error, Toast.LENGTH_LONG).show();
                                bitmap.recycle();
                            }
                        }
                );

            } catch (Exception e) {
                isAnalyzing = false;
                Log.e(TAG, "Analysis error", e);
                updateNotification("分析失败: " + e.getMessage());
                bitmap.recycle();
            }
        });
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
    }

    private void showResultNotification(String result) {
        String displayText = result.length() > 200 ? result.substring(0, 200) + "..." : result;

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setAction(ACTION_OPEN);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("📸 截图分析结果")
                .setContentText(displayText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(displayText))
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_notification, "复制结果", PendingIntent.getActivity(
                        this, 1,
                        new Intent(this, MainActivity.class).setAction("COPY").putExtra("result", result),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .build();

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID + 1, notification);
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("S-master")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(Notification.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        nm.notify(NOTIFICATION_ID, notification);
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("AI 分析结果", text);
        clipboard.setPrimaryClip(clip);
    }

    private final android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_STOP.equals(intent.getAction())) {
                stopSelf();
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception ignored) {}
        }

        if (mediaProjection != null) {
            try {
                mediaProjection.unregisterCallback(projectionCallback);
                mediaProjection.stop();
            } catch (Exception ignored) {}
        }

        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception ignored) {}
        }

        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (Exception ignored) {}
        }

        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }

        if (executor != null) {
            executor.shutdown();
        }

        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {}

        MainActivity.isServiceRunning = false;
    }
}
