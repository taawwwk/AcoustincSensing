package cclab.twkim.aucoustincsensing;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION = 200;
    private Vibrator vibrator; // 진동 API
    private MediaRecorder mediaRecorder; // 레코딩 API
    private String filePath;
    private File recordingsDir;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> recordings;
    private MediaPlayer mediaPlayer;
    private Spinner patternSpinner;
    // 진동 패턴
    private final long[][] vibrationPatterns = {
            {0, 500, 200, 500, 300, 500},
            {0, 300, 100, 300, 100, 300},
            {0, 700, 300, 700, 300, 700}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 앱 시작을 위한 권한 설정
        checkAndRequestPermissions();

        // 앱 기능 설정
        initializeComponents();
    }

    /**
     * 녹음, 파일 쓰기, 진동 권한 설정
     */
    private void checkAndRequestPermissions() {
        // 권한 목록
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.VIBRATE
        };


        ArrayList<String> permissionsToRequest = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSION
            );
        }
    }

    private void initializeComponents() {
        try {

            // 진동 사용을 위한 Vibarator 객체 생성
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

            // 레코딩 파일 저장 경로
            recordingsDir = new File(getExternalFilesDir(null), "recordings");
            if (!recordingsDir.exists()) {
                boolean dirCreated = recordingsDir.mkdirs();
                Log.d(TAG, "Recordings directory created: " + dirCreated);
            }

            // 컴포넌트
            Button startButton = findViewById(R.id.startButton);
            Button stopButton = findViewById(R.id.stopButton);
            ListView listView = findViewById(R.id.listView);
            patternSpinner = findViewById(R.id.patternSpinner);

            // 진동 패턴 설정을 위한 Spinner 초기화
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Pattern 1", "Pattern 2", "Pattern 3"});
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            patternSpinner.setAdapter(spinnerAdapter);

            // 레코딩 파일을 불러올 Listview를 Adapting
            recordings = new ArrayList<>();

            // 레코딩 파일 호출
            loadRecordings();
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, recordings);
            listView.setAdapter(adapter);

            // 컴포넌트, 함수 매핑
            startButton.setOnClickListener(v -> startVibrationAndRecording());
            stopButton.setOnClickListener(v -> stopRecording());
            listView.setOnItemClickListener((parent, view, position, id) -> playRecording(recordings.get(position))); // 리스트뷰의 아이템 클릭 시 오디오 재생

        } catch (Exception e) {
            Log.e(TAG, "Error initializing components", e);
            Toast.makeText(this, "초기화 중 오류 발생", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 진동, 녹음 시작 함수
     */
    private void startVibrationAndRecording() {
        try {
            // Spinner로 설정한 패턴으로 진동 시작
            int selectedPatternIndex = patternSpinner.getSelectedItemPosition();
            long[] pattern = vibrationPatterns[selectedPatternIndex];
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            }

            // 레코딩 설정
            filePath = new File(recordingsDir, System.currentTimeMillis() + ".mp3").getAbsolutePath();
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC); // 오디오 소스 설정
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP); // 저장할 파일의 포맷 설정
            mediaRecorder.setOutputFile(filePath);
            // 오디오 형태로 인코딩. 미설정 시 파일에 오디오 트랙이 누락됨
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            mediaRecorder.prepare(); // 레코딩 준비
            mediaRecorder.start(); // 레코딩  시작
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording", e);
            Toast.makeText(this, "녹음 시작 중 오류 발생", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * STOP 함수 호출 시, 진동과 레코딩을 멈추고 레코딩한 파일을 저장
     * 리스트뷰에 저장된 파일을 갱신
     */
    private void stopRecording() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                recordings.add(new File(filePath).getName());
                vibrator.cancel();
                adapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording", e);
            Toast.makeText(this, "녹음 중지 중 오류 발생", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * @param fileName
     * 오디오 재생 함수
     */
    private void playRecording(String fileName) {
        try {
            File file = new File(recordingsDir, fileName);
            if (file.exists()) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(file.getAbsolutePath());
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing recording", e);
            Toast.makeText(this, "녹음 재생 중 오류 발생", Toast.LENGTH_SHORT).show();
        }
    }

    // 오디오 파일 호출 및 설정
    private void loadRecordings() {
        String[] files = recordingsDir.list();
        if (files != null) {
            recordings.addAll(Arrays.asList(files));
        }
    }

    // 앱 내 권한 체크
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (!allPermissionsGranted) {
                Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                // finish()
            }
        }
    }
}