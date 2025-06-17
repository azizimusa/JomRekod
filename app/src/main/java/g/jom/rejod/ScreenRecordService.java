package g.jom.rejod;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class ScreenRecordService extends Service implements EventManager.Listener{
    private static final String TAG = "ScreenRecordService";
    private static final String CHANNEL_ID = "ScreenRecordServiceChannel";
    private static final int NOTIFICATION_ID = 123;

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;
    private MediaProjection.Callback mediaProjectionCallback;

    private boolean isRecorderStarted = false;

    // MODIFIED: Use these variables for a capped recording resolution.
    private int recordingWidth;
    private int recordingHeight;
    private int screenDensity;

    private int resultCode;
    private Intent data;
    private String videoFilePath;

    @Override
    public void onCreate() {
        super.onCreate();
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(metrics);
            screenDensity = metrics.densityDpi;

            // --- MODIFIED: Calculate a capped resolution to control file size ---
            // We cap the longer side to 1280 pixels, and calculate the other side based on aspect ratio.
            // 720p is a good balance of quality and size. For higher quality, you can use 1920 (1080p).
            final int MAX_RESOLUTION = 720;

            float screenWidth = metrics.widthPixels;
            float screenHeight = metrics.heightPixels;
            float aspectRatio = screenWidth / screenHeight;

            if (screenWidth > screenHeight) { // Landscape
                recordingWidth = MAX_RESOLUTION;
                recordingHeight = (int) (recordingWidth / aspectRatio);
            } else { // Portrait
                recordingHeight = MAX_RESOLUTION;
                recordingWidth = (int) (recordingHeight * aspectRatio);
            }

            // Encoders often require even dimensions
            if (recordingWidth % 2 != 0) recordingWidth--;
            if (recordingHeight % 2 != 0) recordingHeight--;

            Log.d(TAG, "Device resolution: " + screenWidth + "x" + screenHeight + ", Capped recording to: " + recordingWidth + "x" + recordingHeight);
            // --- END MODIFICATION ---
        }
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.e(TAG, "onStartCommand: Intent is null, stopping service.");
            stopSelf();
            return START_NOT_STICKY;
        }

        EventManager.getInstance().register(this);

        resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
        data = intent.getParcelableExtra("data");

        if (resultCode == Activity.RESULT_CANCELED || data == null) {
            Log.e(TAG, "Failed to get permission token (resultCode or data is invalid).");
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundService();

        return START_STICKY;
    }

    private void startRecording() {
        Log.d(TAG, "Starting screen recording...");
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection could not be created. Is permission granted?");
                stopSelf();
                return;
            }

            mediaProjectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                    Log.w(TAG, "MediaProjection session stopped by user or system.");
                    stopRecording();
                }
            };
            mediaProjection.registerCallback(mediaProjectionCallback, new Handler(Looper.getMainLooper()));

            initMediaRecorder();
            createVirtualDisplay();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (mediaRecorder != null) {
                        mediaRecorder.start();
                        isRecorderStarted = true;
                        Log.d(TAG, "Screen recording started successfully after delay.");
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Failed to start MediaRecorder after delay.", e);
                    stopRecording();
                }
            }, 300);

        } catch (IOException e) {
            Log.e(TAG, "Error setting up recording", e);
            stopRecording();
        }
    }

    private void initMediaRecorder() throws IOException {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        videoFilePath = createVideoFilePath();
        if (videoFilePath == null) {
            throw new IOException("Failed to create video file path.");
        }
        mediaRecorder.setOutputFile(videoFilePath);

        // --- MODIFIED: Apply optimized settings ---
        // 1. Use the capped resolution calculated in onCreate.
        mediaRecorder.setVideoSize(recordingWidth, recordingHeight);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            // 2. Set an explicit audio bitrate. 128kbps is good quality for voice.
            mediaRecorder.setAudioEncodingBitRate(128 * 1024);
        } catch (Exception ignored) {
            Log.w(TAG, "Audio source setup failed. Recording without audio.");
        }

        // 3. Lower the video bitrate. 8Mbps is high. 2.5Mbps (2.5 * 1024 * 1024) is a good starting point for 720p.
        // A higher bitrate like 4Mbps will give better quality when paused. Let's use 4Mbps.
        mediaRecorder.setVideoEncodingBitRate(4 * 1024 * 1024);

        // 4. Lower the frame rate. 24fps is sufficient for most screen recordings.
        mediaRecorder.setVideoFrameRate(24);
        // --- END MODIFICATION ---

        mediaRecorder.prepare();
        Log.d(TAG, "MediaRecorder prepared successfully.");
    }

    private String createVideoFilePath() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "ScreenRecord_" + timeStamp + ".mp4";

        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            Log.e(TAG, "Failed to get external files directory.");
            return null;
        }

        File videoFile = new File(dir, fileName);
        return videoFile.getAbsolutePath();
    }

    private void createVirtualDisplay() {
        if (mediaProjection == null) {
            Log.e(TAG, "Cannot create virtual display, MediaProjection is null.");
            return;
        }
        // MODIFIED: Use the same capped resolution for the virtual display.
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenRecordService",
                recordingWidth, recordingHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(), null, null);
        Log.d(TAG, "Virtual display created.");
    }

    private void stopRecording() {
        Log.d(TAG, "Stopping screen recording...");
        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (mediaRecorder != null) {
                if (isRecorderStarted) {
                    try {
                        mediaRecorder.stop();
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "MediaRecorder stop failed", e);
                    }
                }
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
                isRecorderStarted = false;
            }
            if (mediaProjection != null) {
                if (mediaProjectionCallback != null) {
                    mediaProjection.unregisterCallback(mediaProjectionCallback);
                    mediaProjectionCallback = null;
                }
                mediaProjection.stop();
                mediaProjection = null;
            }

            if (videoFilePath != null) {
                Log.d(TAG, "Recording saved successfully to: " + videoFilePath);
                Intent intent = new Intent(MainActivity.ACTION_RECORDING_FINISHED);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                videoFilePath = null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error while stopping recording", e);
        } finally {
            stopForeground(true);
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Record Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for the screen recording service.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Recording")
                .setContentText("Your screen is being recorded.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();
    }

    private void startForegroundService() {
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        Log.d(TAG, "Service started in foreground.");
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
        EventManager.getInstance().unregister(this);
        Log.d(TAG, "Service destroyed.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onEvent(EventManager.Event event) {
        if (event instanceof EventManager.StartRecordingEvent) {
            startRecording();
        }
    }
}
