//package g.jom.rejod;
//
//import android.app.Activity;
//import android.app.AlertDialog;
//import android.content.Context;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.media.projection.MediaProjectionManager;
//import android.os.Build;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.Toast;
//
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//import android.Manifest;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class MainActivity extends AppCompatActivity {
//    private Button btnPermission, startButton, stopButton;
//
//    private boolean isRecording = false;
//
//    // ActivityResultLauncher for requesting permissions
//    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
//            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
//                boolean allPermissionsGranted = true;
//                for (Boolean granted : permissions.values()) {
//                    if (!granted) {
//                        allPermissionsGranted = false;
//                        break;
//                    }
//                }
//
//                if (allPermissionsGranted) {
//                    // Permissions are granted, now we can launch the media projection
//                    launchMediaProjectionIntent();
//                } else {
//                    Toast.makeText(this, "Permissions are required to record the screen.", Toast.LENGTH_SHORT).show();
//                }
//            });
//
//    // ActivityResultLauncher for the screen capture intent
//    private final ActivityResultLauncher<Intent> mediaProjectionLauncher =
//            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
//                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
//                    // Permission granted, start the service
//                    Intent serviceIntent = new Intent(this, ScreenRecordService.class);
//                    serviceIntent.putExtra("resultCode", result.getResultCode());
//                    serviceIntent.putExtra("data", result.getData());
//
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                        startForegroundService(serviceIntent);
//                    } else {
//                        startService(serviceIntent);
//                    }
//                    isRecording = true;
//                    updateButtonStates();
//                } else {
//                    Toast.makeText(this, "Screen capture permission was denied.", Toast.LENGTH_SHORT).show();
//                }
//            });
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        btnPermission = findViewById(R.id.btnPermission);
//        startButton = findViewById(R.id.startButton);
//        stopButton = findViewById(R.id.stopButton);
//
//        updateButtonStates();
//
//        btnPermission.setOnClickListener(v -> {
//            if (isRecording) {
//                Toast.makeText(this, "Already recording.", Toast.LENGTH_SHORT).show();
//            } else {
//                // Check and request permissions before starting
//                checkAndRequestPermissions();
//            }
//        });
//
//        startButton.setOnClickListener(v -> {
//            EventManager.getInstance().post(new EventManager.StartRecordingEvent());
//        });
//
//        stopButton.setOnClickListener(v -> {
//            if (isRecording) {
//                // Stop the service
//                Intent serviceIntent = new Intent(this, ScreenRecordService.class);
//                stopService(serviceIntent);
//                isRecording = false;
//                updateButtonStates();
//            }
//        });
//    }
//
//    private void checkAndRequestPermissions() {
//        List<String> permissionsNeeded = new ArrayList<>();
//
//        // Audio recording permission
////        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
////            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
////        }
//
//        // Notification permission for Android 13+ (for foreground service notification)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
//            }
//        }
//
//        if (!permissionsNeeded.isEmpty()) {
//            requestPermissionsLauncher.launch(permissionsNeeded.toArray(new String[0]));
//        } else {
//            // All permissions are already granted
//            launchMediaProjectionIntent();
//        }
//    }
//
//    private void launchMediaProjectionIntent() {
//        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
//        if (mediaProjectionManager != null) {
//            Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
//            mediaProjectionLauncher.launch(screenCaptureIntent);
//        }
//    }
//
//    private void updateButtonStates() {
////        if (isRecording) {
////            startButton.setEnabled(false);
////            stopButton.setEnabled(true);
////        } else {
////            startButton.setEnabled(true);
////            stopButton.setEnabled(false);
////        }
//    }
//}
