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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.s_master.ai.VisionAnalyzer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingService extends Service {

    private static final String TAG = "FloatingService";
    private static final String CHANNEL_ID = "S_master_channel";
    private static final String CHANNEL_NAME = "S-master Service";
    private static final int NOTIFICATION_ID = 1001;
    private static final int SCREENSHOT_RETRY_COUNT = 2;
    private static final long SCREENSHOT_RETRY_DELAY_MS = 300;

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
    private long touchStartTime;

    private volatile boolean isAnalyzing = false;
    private volatile boolean isTakingScreenshot = false;
    private int screenshotRetryCount = 0;
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
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = getSavedPositionX();
        params.y = getSavedPositionY();

        ImageView btn = floatingView.findViewById(R.id.floatingBtn);
        
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging = false;
                    touchStartX = event.getRawX();
                    touchStartY = event.getRawY();
                    initialX = params.x;
                    initialY = params.y;
                    touchStartTime = System.currentTimeMillis();
                    animateButtonPressed(v, true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - touchStartX;
                    float deltaY = event.getRawY() - touchStartY;

                    if (Math.abs(deltaX) > 8 || Math.abs(deltaY) > 8) {
                        isDragging = true;
                        animateButtonPressed(v, false);
                    }

                    if (isDragging) {
                        params.x = initialX + (int) deltaX;
                        params.y = initialY + (int) deltaY;
                        
                        params.x = Math.max(0, Math.min(params.x, 
                                getScreenWidth() - v.getWidth()));
                        params.y = Math.max(getStatusBarHeight(), 
                                Math.min(params.y, getScreenHeight() - v.getHeight()));
                        
                        windowManager.updateViewLayout(floatingView, params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    animateButtonPressed(v, false);
                    long touchDuration = System.currentTimeMillis() - touchStartTime;
                    
                    if (!isDragging && touchDuration < 500) {
                        onFloatingClick();
                    } else if (isDragging) {
                        savePosition(params.x, params.y);
                        snapToEdge();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    animateButtonPressed(v, false);
                    return true;
            }
            return false;
        });

        windowManager.addView(floatingView, params);
    }

    private void animateButtonPressed(View view, boolean pressed) {
        if (view instanceof ImageView) {
            float scale = pressed ? 0.9f : 1.0f;
            view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(100)
                    .start();
        }
    }

    private void snapToEdge() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int viewWidth = floatingView.getWidth();
        int centerX = params.x + viewWidth / 2;

        int newX = centerX < screenWidth / 2 ? 0 : screenWidth - viewWidth;
        
        params.x = newX;
        windowManager.updateViewLayout(floatingView, params);
        savePosition(params.x, params.y);
    }

    private int getScreenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int getScreenHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void savePosition(int x, int y) {
        getSharedPreferences("S_masterPrefs", MODE_PRIVATE)
                .edit()
                .putInt("floating_x", x)
                .putInt("floating_y", y)
                .apply();
    }

    private int getSavedPositionX() {
        return getSharedPreferences("S_masterPrefs", MODE_PRIVATE)
                .getInt("floating_x", 100);
    }

    private int getSavedPositionY() {
        return getSharedPreferences("S_masterPrefs", MODE_PRIVATE)
                .getInt("floating_y", 300);
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
                    runOnUiThread(() -> {
                        Toast.makeText(FloatingService.this, "屏幕录制权限已失效，请重启服务", Toast.LENGTH_LONG).show();
                    });
                    stopSelf();
                }
            };
            mediaProjection.registerCallback(projectionCallback, backgroundHandler);

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

            imageReader.setOnImageAvailableListener(this::handleImageAvailable, backgroundHandler);

            Log.d(TAG, "MediaProjection setup complete");

        } catch (Exception e) {
            Log.e(TAG, "Failed to setup MediaProjection", e);
            Toast.makeText(this, "屏幕投影初始化失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleImageAvailable(ImageReader reader) {
        if (!isTakingScreenshot || isAnalyzing) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                try {
                    image.close();
                } catch (Exception ignored) {}
            }
            return;
        }

        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image != null) {
                Bitmap bitmap = imageToBitmap(image);
                if (bitmap != null) {
                    screenshotRetryCount = 0;
                    isTakingScreenshot = false;
                    analyzeImage(bitmap);
                } else {
                    retryScreenshot();
                }
            } else {
                retryScreenshot();
            }
        } catch (Exception e) {
            Log.e(TAG, "Screenshot error", e);
            retryScreenshot();
        } finally {
            if (image != null) {
                try {
                    image.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private void retryScreenshot() {
        if (screenshotRetryCount < SCREENSHOT_RETRY_COUNT) {
            screenshotRetryCount++;
            Log.d(TAG, "Retrying screenshot, attempt " + screenshotRetryCount);
            backgroundHandler.postDelayed(() -> {
                if (isTakingScreenshot && imageReader != null) {
                    Image image = imageReader.acquireLatestImage();
                    if (image != null) {
                        try {
                            Bitmap bitmap = imageToBitmap(image);
                            if (bitmap != null) {
                                screenshotRetryCount = 0;
                                isTakingScreenshot = false;
                                analyzeImage(bitmap);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Retry screenshot error", e);
                        } finally {
                            image.close();
                        }
                    }
                }
            }, SCREENSHOT_RETRY_DELAY_MS);
        } else {
            isTakingScreenshot = false;
            isAnalyzing = false;
            screenshotRetryCount = 0;
            runOnUiThread(() -> {
                Toast.makeText(FloatingService.this, "截图失败，请重试", Toast.LENGTH_SHORT).show();
                updateNotification("截图失败");
            });
        }
    }

    private void runOnUiThread(Runnable runnable) {
        new Handler(getMainLooper()).post(runnable);
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

            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            
            if (rowPadding > 0) {
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                bitmap.recycle();
                return croppedBitmap;
            }
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Image to bitmap error", e);
            return null;
        }
    }

    private void takeScreenshot() {
        if (imageReader == null) {
            Toast.makeText(this, "截图服务未就绪", Toast.LENGTH_SHORT).show();
            return;
        }

        isTakingScreenshot = true;
        screenshotRetryCount = 0;
        isAnalyzing = true;
        
        updateNotification("正在截取屏幕...");
        Toast.makeText(this, "📸 正在截取屏幕...", Toast.LENGTH_SHORT).show();
    }

    private void analyzeImage(Bitmap bitmap) {
        executor.execute(() -> {
            try {
                updateNotification("正在分析...");

                String base64Image = bitmapToBase64(bitmap);
                Log.d(TAG, "Image size: " + base64Image.length() + " bytes");

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
                                updateNotification("分析完成");

                                if (MainActivity.isSilentMode) {
                                    copyToClipboard(result);
                                    runOnUiThread(() -> 
                                        Toast.makeText(FloatingService.this, "结果已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                    );
                                } else {
                                    showResultNotification(result);
                                }

                                bitmap.recycle();
                            }

                            @Override
                            public void onError(String error) {
                                isAnalyzing = false;
                                updateNotification("分析失败");
                                runOnUiThread(() -> 
                                    Toast.makeText(FloatingService.this, "分析失败: " + error, Toast.LENGTH_LONG).show()
                                );
                                bitmap.recycle();
                            }

                            @Override
                            public void onProgress(int progress) {
                                updateNotification("分析中... " + progress + "%");
                            }
                        }
                );

            } catch (Exception e) {
                isAnalyzing = false;
                Log.e(TAG, "Analysis error", e);
                updateNotification("分析失败: " + e.getMessage());
                runOnUiThread(() -> 
                    Toast.makeText(FloatingService.this, "分析失败: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
                bitmap.recycle();
            }
        });
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
    }

    private void showResultNotification(String result) {
        String displayText = result.length() > 200 ? result.substring(0, 200) + "..." : result;

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setAction(ACTION_OPEN);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent copyIntent = new Intent(this, MainActivity.class);
        copyIntent.setAction("COPY");
        copyIntent.putExtra("result", result);
        PendingIntent copyPending = PendingIntent.getActivity(this, 1, copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("📸 截图分析结果")
                .setContentText(displayText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(result))
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_notification, "复制", copyPending)
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

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
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

        if (mediaProjection != null) {
            try {
                mediaProjection.unregisterCallback(projectionCallback);
                mediaProjection.stop();
            } catch (Exception ignored) {}
        }

        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (executor != null) {
            executor.shutdownNow();
        }

        MainActivity.isServiceRunning = false;
    }
}
