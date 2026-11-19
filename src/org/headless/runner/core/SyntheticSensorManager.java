package org.headless.runner.core;

import android.hardware.*;
import android.os.Handler;
import android.os.MemoryFile;

import java.util.Collections;
import java.util.List;

/**
 * Pure Java SensorManager implementation for headless runner execution.
 * Avoids any JNI / SystemSensorManager nativeClassInit / nativeCreate calls.
 */
public class SyntheticSensorManager extends SensorManager {

    private static final List<Sensor> EMPTY_SENSORS = Collections.emptyList();

    @Override
    protected List<Sensor> getFullSensorList() {
        return EMPTY_SENSORS;
    }

    @Override
    protected List<Sensor> getFullDynamicSensorList() {
        return EMPTY_SENSORS;
    }

    @Override
    protected boolean registerListenerImpl(SensorEventListener listener, Sensor sensor,
                                          int delayUs, Handler handler, int maxReportLatencyUs, int reservedFlags) {
        return true;
    }

    @Override
    protected void unregisterListenerImpl(SensorEventListener listener, Sensor sensor) {
    }

    @Override
    protected boolean flushImpl(SensorEventListener listener) {
        return true;
    }

    @Override
    protected SensorDirectChannel createDirectChannelImpl(MemoryFile memoryFile, HardwareBuffer hardwareBuffer) {
        return null;
    }

    @Override
    protected void destroyDirectChannelImpl(SensorDirectChannel channel) {
    }

    @Override
    protected int configureDirectChannelImpl(SensorDirectChannel channel, Sensor sensor, int rate) {
        return 0;
    }

    @Override
    protected void registerDynamicSensorCallbackImpl(DynamicSensorCallback callback, Handler handler) {
    }

    @Override
    protected void unregisterDynamicSensorCallbackImpl(DynamicSensorCallback callback) {
    }

    @Override
    protected boolean requestTriggerSensorImpl(TriggerEventListener listener, Sensor sensor) {
        return true;
    }

    @Override
    protected boolean cancelTriggerSensorImpl(TriggerEventListener listener, Sensor sensor, boolean max) {
        return true;
    }

    @Override
    protected boolean initDataInjectionImpl(boolean enable) {
        return true;
    }

    @Override
    protected boolean injectSensorDataImpl(Sensor sensor, float[] values, int accuracy, long timestamp) {
        return true;
    }

    @Override
    protected boolean setOperationParameterImpl(SensorAdditionalInfo parameter) {
        return true;
    }
}
