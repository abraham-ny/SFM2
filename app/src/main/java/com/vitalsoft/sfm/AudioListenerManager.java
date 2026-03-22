package com.vitalsoft.sfm;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

public class AudioListenerManager {

    private static final String TAG = "SFM_AudioListener";

    public interface Callback {
        void onResult(String transcribedText);
        void onError(String error);
        void onAmplitude(int amplitude);
    }

    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private AmplitudeMonitor amplitudeMonitor;
    private volatile boolean isListening = false;
    private volatile boolean rmsActive = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AudioListenerManager(Context context) {
        this.context = context;
    }

    public void startListening(final int durationMs, final Callback callback) {
        if (isListening) stopListening();
        isListening = true;
        rmsActive = false;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                startSpeechRecognition(durationMs, callback);
            }
        });

        startAmplitudeMonitor(callback);
    }

    private void startSpeechRecognition(final int durationMs, final Callback callback) {
        destroySpeechRecognizer();

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer unavailable — amplitude-only mode");
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isListening) {
                        isListening = false;
                        stopAmplitudeMonitor();
                        callback.onResult("");
                    }
                }
            }, durationMs);
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "Speech began");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                rmsActive = true;
                int amp = (int) Math.min(100, Math.max(0, (rmsdB + 2f) * 8f));
                callback.onAmplitude(amp);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "End of speech");
            }

            @Override
            public void onError(int error) {
                isListening = false;
                rmsActive = false;
                stopAmplitudeMonitor();
                String msg = speechErrorMessage(error);
                Log.e(TAG, "Speech error: " + msg + " (" + error + ")");
                if (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    callback.onResult("");
                } else {
                    callback.onError(msg);
                }
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                rmsActive = false;
                stopAmplitudeMonitor();
                ArrayList<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    int limit = Math.min(matches.size(), 3);
                    for (int i = 0; i < limit; i++) {
                        if (i > 0) sb.append(" ");
                        sb.append(matches.get(i));
                    }
                    Log.d(TAG, "STT results: " + sb.toString());
                    callback.onResult(sb.toString());
                } else {
                    callback.onResult("");
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) {
                    Log.d(TAG, "Partial: " + partial.get(0));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L);
        intent.putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            (long) Math.min(durationMs, 5000));
        intent.putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            (long) Math.min(durationMs / 2, 3000));

        try {
            speechRecognizer.startListening(intent);
            Log.d(TAG, "SpeechRecognizer started");
        } catch (Exception e) {
            Log.e(TAG, "startListening() threw: " + e.getMessage());
            isListening = false;
            stopAmplitudeMonitor();
            callback.onError("Failed to start recognition: " + e.getMessage());
        }
    }

    private void startAmplitudeMonitor(final Callback callback) {
        amplitudeMonitor = new AmplitudeMonitor(context.getCacheDir());
        amplitudeMonitor.start(new AmplitudeMonitor.Listener() {
            @Override
            public void onAmplitude(final int amp) {
                if (!rmsActive && isListening) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onAmplitude(amp);
                        }
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "AmplitudeMonitor (non-fatal): " + message);
            }
        });
    }

    private void stopAmplitudeMonitor() {
        if (amplitudeMonitor != null) {
            amplitudeMonitor.stop();
            amplitudeMonitor = null;
        }
    }

    public void stopListening() {
        isListening = false;
        rmsActive = false;
        stopAmplitudeMonitor();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                destroySpeechRecognizer();
            }
        });
    }

    private void destroySpeechRecognizer() {
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception e) {}
            try { speechRecognizer.cancel();        } catch (Exception e) {}
            try { speechRecognizer.destroy();       } catch (Exception e) {}
            speechRecognizer = null;
        }
    }

    private static String speechErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:                    return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:                   return "Client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Missing mic permission";
            case SpeechRecognizer.ERROR_NETWORK:                  return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:          return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:                 return "No match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:          return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER:                   return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:           return "No speech detected";
            default:                                               return "Error " + error;
        }
    }
}
