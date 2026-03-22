package com.vitalsoft.sfm;

import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * Uses MediaRecorder to capture amplitude when SpeechRecognizer is unavailable.
 * Records to a temp file; the actual audio isn't used — we only poll getMaxAmplitude().
 */
public class AmplitudeMonitor {

    private static final String TAG = "SFM_Amplitude";
    private static final int POLL_INTERVAL_MS = 80;

    public interface Listener {
        void onAmplitude(int amp0to100);
        void onError(String message);
    }

    private MediaRecorder recorder;
    private Thread pollThread;
    private volatile boolean running = false;
    private final File tempFile;

    public AmplitudeMonitor(File cacheDir) {
        tempFile = new File(cacheDir, "sfm_amp_tmp.3gp");
    }

    public void start(final Listener listener) {
        if (running) return;

        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(tempFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            running = true;
            Log.d(TAG, "AmplitudeMonitor started");
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed: " + e.getMessage());
            listener.onError("Microphone unavailable: " + e.getMessage());
            release();
            return;
        } catch (IllegalStateException e) {
            Log.e(TAG, "start() failed: " + e.getMessage());
            listener.onError("Could not start recorder: " + e.getMessage());
            release();
            return;
        }

        pollThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                    if (recorder != null && running) {
                        try {
                            int maxAmp = recorder.getMaxAmplitude(); // 0–32767
                            int normalized = (int) Math.min(100, (maxAmp / 327.0));
                            listener.onAmplitude(normalized);
                        } catch (IllegalStateException e) {
                            // recorder may have been released
                            break;
                        }
                    }
                }
            }
        });
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
        release();
    }

    private void release() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (IllegalStateException e) {
                // already stopped or never started — safe to ignore
            }
            try {
                recorder.release();
            } catch (Exception e) {
                Log.e(TAG, "release error: " + e.getMessage());
            }
            recorder = null;
        }
        // Clean up temp file
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }
}
