package g.jom.rejod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final String ACTION_RECORDING_FINISHED = "g.jom.rejod.RECORDING_FINISHED";
    private Button startButton, stopButton, btnPermission;
    private RecyclerView videoRecyclerView;
    private VideoAdapter videoAdapter;
    private List<Uri> videoList = new ArrayList<>();
    private VideoView videoPlayer;

    private boolean isRecording = false;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Boolean granted : permissions.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    launchMediaProjectionIntent();
                } else {
                    Toast.makeText(this, "Recording permissions are required.", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> mediaProjectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent serviceIntent = new Intent(this, ScreenRecordService.class);
                    serviceIntent.putExtra("resultCode", result.getResultCode());
                    serviceIntent.putExtra("data", result.getData());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                    isRecording = true;
                    updateButtonStates();
                } else {
                    Toast.makeText(this, "Screen capture permission was denied.", Toast.LENGTH_SHORT).show();
                }
            });

    private final BroadcastReceiver recordingFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(MainActivity.this, "Recording finished, refreshing list...", Toast.LENGTH_SHORT).show();
            loadVideos();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPermission = findViewById(R.id.btnPermission);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        videoRecyclerView = findViewById(R.id.videoRecyclerView);
        videoPlayer = findViewById(R.id.videoPlayer);

        setupRecyclerView();
        updateButtonStates();
        loadVideos(); // Load any existing videos on startup

        btnPermission.setOnClickListener(v -> {
            if (!isRecording) {
                checkAndRequestRecordingPermissions();
            }
        });

        startButton.setOnClickListener(v -> {
            EventManager.getInstance().post(new EventManager.StartRecordingEvent());
        });

        stopButton.setOnClickListener(v -> {
            if (isRecording) {
                Intent serviceIntent = new Intent(this, ScreenRecordService.class);
                stopService(serviceIntent);
                isRecording = false;
                updateButtonStates();
            }
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(recordingFinishedReceiver,
                new IntentFilter(ACTION_RECORDING_FINISHED));
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(recordingFinishedReceiver);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (videoPlayer.getVisibility() == View.VISIBLE) {
            videoPlayer.stopPlayback();
            videoPlayer.setVisibility(View.GONE);
            videoRecyclerView.setVisibility(View.VISIBLE);
        } else {
            super.onBackPressed();
        }
    }


    private void setupRecyclerView() {
        videoAdapter = new VideoAdapter(videoList, uri -> {
            playVideo(uri);
        });
        videoRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        videoRecyclerView.setAdapter(videoAdapter);
    }

    private void playVideo(Uri videoUri) {
        videoRecyclerView.setVisibility(View.GONE);
        videoPlayer.setVisibility(View.VISIBLE);
        videoPlayer.setVideoURI(videoUri);
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoPlayer);
        videoPlayer.setMediaController(mediaController);
        videoPlayer.setOnCompletionListener(mp -> {
            videoPlayer.setVisibility(View.GONE);
            videoRecyclerView.setVisibility(View.VISIBLE);
        });
        videoPlayer.start();
    }


    private void checkAndRequestRecordingPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
//            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
//        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            requestPermissionsLauncher.launch(permissionsNeeded.toArray(new String[0]));
        } else {
            launchMediaProjectionIntent();
        }
    }

    private void launchMediaProjectionIntent() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent());
        }
    }

    private void loadVideos() {
        videoList.clear();
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        if (storageDir != null) {
            File[] videoFiles = storageDir.listFiles((dir, name) -> name.endsWith(".mp4"));

            if (videoFiles != null && videoFiles.length > 0) {
                // Sort files by date, newest first
                Arrays.sort(videoFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                for (File file : videoFiles) {
                    videoList.add(Uri.fromFile(file));
                }
            }
        }

        videoAdapter.notifyDataSetChanged();
        if(videoList.isEmpty()) {
            Toast.makeText(this, "No recordings found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateButtonStates() {
//        startButton.setEnabled(!isRecording);
//        stopButton.setEnabled(isRecording);
    }
}
