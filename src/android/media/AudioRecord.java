package android.media;

import android.os.Handler;
import android.os.PersistableBundle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Pure Java in-process AudioRecord implementation.
 * Eliminates UnsatisfiedLinkError: native_get_min_buff_size and other audio_jni calls across OpenJDK runtimes.
 */
public class AudioRecord implements AudioRouting, MicrophoneDirection, AudioRecordingMonitor, AudioRecordingMonitorClient {

    public static final int STATE_UNINITIALIZED = 0;
    public static final int STATE_INITIALIZED = 1;

    public static final int RECORDSTATE_STOPPED = 1;
    public static final int RECORDSTATE_RECORDING = 3;

    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int ERROR_BAD_VALUE = -2;
    public static final int ERROR_INVALID_OPERATION = -3;
    public static final int ERROR_DEAD_OBJECT = -6;

    public static final String SUBMIX_FIXED_VOLUME = "fixedVolume";

    public static final int READ_BLOCKING = 0;
    public static final int READ_NON_BLOCKING = 1;

    private int mState = STATE_INITIALIZED;
    private int mRecordingState = RECORDSTATE_STOPPED;
    private int mSampleRate = 44100;
    private int mChannelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int mSessionId = 0;
    private int mAudioSource = 0;
    private int mBufferSizeInBytes = 4096;

    public AudioRecord(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes)
            throws IllegalArgumentException {
        this.mAudioSource = audioSource;
        this.mSampleRate = sampleRateInHz;
        this.mChannelConfig = channelConfig;
        this.mAudioFormat = audioFormat;
        this.mBufferSizeInBytes = Math.max(bufferSizeInBytes, 1024);
        this.mState = STATE_INITIALIZED;
    }

    public AudioRecord(AudioAttributes attributes, AudioFormat format, int bufferSizeInBytes, int sessionId)
            throws IllegalArgumentException {
        if (format != null) {
            this.mSampleRate = format.getSampleRate();
            this.mChannelConfig = format.getChannelMask();
            this.mAudioFormat = format.getEncoding();
        }
        this.mBufferSizeInBytes = Math.max(bufferSizeInBytes, 1024);
        this.mSessionId = sessionId;
        this.mState = STATE_INITIALIZED;
    }

    AudioRecord(long nativeRecordInJavaObj) {
        this.mState = STATE_INITIALIZED;
    }

    void deferred_connect(long nativeRecordInJavaObj) {}

    public void release() {
        mState = STATE_UNINITIALIZED;
        mRecordingState = RECORDSTATE_STOPPED;
    }

    @Override
    protected void finalize() {}

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getAudioSource() {
        return mAudioSource;
    }

    public int getAudioFormat() {
        return mAudioFormat;
    }

    public int getChannelConfiguration() {
        return mChannelConfig;
    }

    public AudioFormat getFormat() {
        return new AudioFormat.Builder()
                .setSampleRate(mSampleRate)
                .setEncoding(mAudioFormat)
                .setChannelMask(mChannelConfig)
                .build();
    }

    public int getChannelCount() {
        return (mChannelConfig == AudioFormat.CHANNEL_IN_STEREO) ? 2 : 1;
    }

    public int getState() {
        return mState;
    }

    public int getRecordingState() {
        return mRecordingState;
    }

    public int getBufferSizeInFrames() {
        return mBufferSizeInBytes / (getChannelCount() * 2);
    }

    public int getNotificationMarkerPosition() {
        return 0;
    }

    public int getPositionNotificationPeriod() {
        return 0;
    }

    public int getTimestamp(AudioTimestamp outTimestamp, int timebase) {
        return ERROR_INVALID_OPERATION;
    }

    public static int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat) {
        int channelCount = 1;
        switch (channelConfig) {
            case AudioFormat.CHANNEL_IN_DEFAULT:
            case AudioFormat.CHANNEL_IN_MONO:
            case AudioFormat.CHANNEL_CONFIGURATION_MONO:
                channelCount = 1;
                break;
            case AudioFormat.CHANNEL_IN_STEREO:
            case AudioFormat.CHANNEL_CONFIGURATION_STEREO:
            case (AudioFormat.CHANNEL_IN_FRONT | AudioFormat.CHANNEL_IN_BACK):
                channelCount = 2;
                break;
            default:
                if ((channelConfig & (AudioFormat.CHANNEL_IN_MONO | AudioFormat.CHANNEL_IN_STEREO)) != 0) {
                    channelCount = Integer.bitCount(channelConfig);
                } else {
                    return ERROR_BAD_VALUE;
                }
        }

        int sizeInBytes = 2;
        switch (audioFormat) {
            case AudioFormat.ENCODING_PCM_8BIT:
                sizeInBytes = 1;
                break;
            case AudioFormat.ENCODING_PCM_16BIT:
            case AudioFormat.ENCODING_DEFAULT:
                sizeInBytes = 2;
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
                sizeInBytes = 4;
                break;
            default:
                return ERROR_BAD_VALUE;
        }

        if (sampleRateInHz < 4000 || sampleRateInHz > 192000) {
            return ERROR_BAD_VALUE;
        }

        // Return a standard 100ms buffer size (at least 1024 bytes)
        int bufferSize = (sampleRateInHz * channelCount * sizeInBytes) / 10;
        return Math.max(bufferSize, 1024);
    }

    public int getAudioSessionId() {
        return mSessionId;
    }

    public void startRecording() throws IllegalStateException {
        mRecordingState = RECORDSTATE_RECORDING;
    }

    public void startRecording(MediaSyncEvent syncEvent) throws IllegalStateException {
        mRecordingState = RECORDSTATE_RECORDING;
    }

    public void stop() throws IllegalStateException {
        mRecordingState = RECORDSTATE_STOPPED;
    }

    public int read(byte[] audioData, int offsetInBytes, int sizeInBytes) {
        if (audioData == null || offsetInBytes < 0 || sizeInBytes < 0 || (offsetInBytes + sizeInBytes) > audioData.length) {
            return ERROR_BAD_VALUE;
        }
        java.util.Arrays.fill(audioData, offsetInBytes, offsetInBytes + sizeInBytes, (byte) 0);
        return sizeInBytes;
    }

    public int read(byte[] audioData, int offsetInBytes, int sizeInBytes, int readMode) {
        return read(audioData, offsetInBytes, sizeInBytes);
    }

    public int read(short[] audioData, int offsetInShorts, int sizeInShorts) {
        if (audioData == null || offsetInShorts < 0 || sizeInShorts < 0 || (offsetInShorts + sizeInShorts) > audioData.length) {
            return ERROR_BAD_VALUE;
        }
        java.util.Arrays.fill(audioData, offsetInShorts, offsetInShorts + sizeInShorts, (short) 0);
        return sizeInShorts;
    }

    public int read(short[] audioData, int offsetInShorts, int sizeInShorts, int readMode) {
        return read(audioData, offsetInShorts, sizeInShorts);
    }

    public int read(float[] audioData, int offsetInFloats, int sizeInFloats, int readMode) {
        if (audioData == null || offsetInFloats < 0 || sizeInFloats < 0 || (offsetInFloats + sizeInFloats) > audioData.length) {
            return ERROR_BAD_VALUE;
        }
        java.util.Arrays.fill(audioData, offsetInFloats, offsetInFloats + sizeInFloats, 0.0f);
        return sizeInFloats;
    }

    public int read(ByteBuffer audioBuffer, int sizeInBytes) {
        if (audioBuffer == null || sizeInBytes < 0 || sizeInBytes > audioBuffer.remaining()) {
            return ERROR_BAD_VALUE;
        }
        byte[] zeros = new byte[sizeInBytes];
        audioBuffer.put(zeros);
        return sizeInBytes;
    }

    public int read(ByteBuffer audioBuffer, int sizeInBytes, int readMode) {
        return read(audioBuffer, sizeInBytes);
    }

    public PersistableBundle getMetrics() {
        return new PersistableBundle();
    }

    public void setRecordPositionUpdateListener(OnRecordPositionUpdateListener listener) {}

    public void setRecordPositionUpdateListener(OnRecordPositionUpdateListener listener, Handler handler) {}

    public int setNotificationMarkerPosition(int markerInFrames) {
        return SUCCESS;
    }

    @Override
    public AudioDeviceInfo getRoutedDevice() {
        return null;
    }

    @Override
    public void addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener listener, Handler handler) {}

    @Override
    public void removeOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener listener) {}

    public void addOnRoutingChangedListener(OnRoutingChangedListener listener, Handler handler) {}

    public void removeOnRoutingChangedListener(OnRoutingChangedListener listener) {}

    public int setPositionNotificationPeriod(int periodInFrames) {
        return SUCCESS;
    }

    @Override
    public boolean setPreferredDevice(AudioDeviceInfo deviceInfo) {
        return true;
    }

    @Override
    public AudioDeviceInfo getPreferredDevice() {
        return null;
    }

    public List<MicrophoneInfo> getActiveMicrophones() throws IOException {
        return new ArrayList<>();
    }

    @Override
    public void registerAudioRecordingCallback(Executor executor, AudioManager.AudioRecordingCallback cb) {}

    @Override
    public void unregisterAudioRecordingCallback(AudioManager.AudioRecordingCallback cb) {}

    @Override
    public AudioRecordingConfiguration getActiveRecordingConfiguration() {
        return null;
    }

    @Override
    public int getPortId() {
        return 1;
    }

    @Override
    public boolean setPreferredMicrophoneDirection(int direction) {
        return true;
    }

    @Override
    public boolean setPreferredMicrophoneFieldDimension(float zoom) {
        return true;
    }

    public void native_release() {}

    public interface OnRecordPositionUpdateListener {
        void onMarkerReached(AudioRecord recorder);
        void onPeriodicNotification(AudioRecord recorder);
    }

    public interface OnRoutingChangedListener extends AudioRouting.OnRoutingChangedListener {
        void onRoutingChanged(AudioRecord audioRecord);
        default void onRoutingChanged(AudioRouting router) {}
    }

    public static final class MetricsConstants {
        public static final String ENCODING = "android.media.audiorecord.encoding";
        public static final String SOURCE = "android.media.audiorecord.source";
        public static final String LATENCY = "android.media.audiorecord.latency";
        public static final String SAMPLERATE = "android.media.audiorecord.samplerate";
        public static final String CHANNELS = "android.media.audiorecord.channels";
        public static final String CHANNEL_MASK = "android.media.audiorecord.channelMask";
        public static final String PORT_ID = "android.media.audiorecord.portId";
        public static final String FRAME_COUNT = "android.media.audiorecord.frameCount";
        public static final String ATTRIBUTES = "android.media.audiorecord.attributes";
        public static final String DURATION_MS = "android.media.audiorecord.durationMs";
        public static final String START_COUNT = "android.media.audiorecord.startCount";
    }

    public static class Builder {
        private int mAudioSource = 0;
        private AudioAttributes mAttributes;
        private AudioFormat mFormat;
        private int mBufferSizeInBytes = 4096;
        private int mSessionId = 0;

        public Builder() {}

        public Builder setAudioSource(int source) throws IllegalArgumentException {
            this.mAudioSource = source;
            return this;
        }

        public Builder setAudioAttributes(AudioAttributes attributes) throws IllegalArgumentException {
            this.mAttributes = attributes;
            return this;
        }

        public Builder setAudioFormat(AudioFormat format) throws IllegalArgumentException {
            this.mFormat = format;
            return this;
        }

        public Builder setBufferSizeInBytes(int bufferSizeInBytes) throws IllegalArgumentException {
            this.mBufferSizeInBytes = bufferSizeInBytes;
            return this;
        }

        public Builder setAudioPlaybackCaptureConfig(AudioPlaybackCaptureConfiguration config) {
            return this;
        }

        public Builder setSessionId(int sessionId) throws IllegalArgumentException {
            this.mSessionId = sessionId;
            return this;
        }

        public AudioRecord build() throws UnsupportedOperationException {
            if (mFormat != null) {
                return new AudioRecord(mAttributes, mFormat, mBufferSizeInBytes, mSessionId);
            }
            return new AudioRecord(mAudioSource, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mBufferSizeInBytes);
        }
    }
}
