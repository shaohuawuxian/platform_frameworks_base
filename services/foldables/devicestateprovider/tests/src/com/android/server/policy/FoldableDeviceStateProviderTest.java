/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.policy;


import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.STATE_OFF;
import static android.view.Display.STATE_ON;
import static android.view.Display.TYPE_EXTERNAL;
import static android.view.Display.TYPE_INTERNAL;

import static com.android.server.devicestate.DeviceStateProvider.SUPPORTED_DEVICE_STATES_CHANGED_EXTERNAL_DISPLAY_ADDED;
import static com.android.server.devicestate.DeviceStateProvider.SUPPORTED_DEVICE_STATES_CHANGED_EXTERNAL_DISPLAY_REMOVED;
import static com.android.server.devicestate.DeviceStateProvider.SUPPORTED_DEVICE_STATES_CHANGED_INITIALIZED;
import static com.android.server.devicestate.DeviceStateProvider.SUPPORTED_DEVICE_STATES_CHANGED_POWER_SAVE_DISABLED;
import static com.android.server.devicestate.DeviceStateProvider.SUPPORTED_DEVICE_STATES_CHANGED_POWER_SAVE_ENABLED;
import static com.android.server.devicestate.DeviceStateProvider.SUPPORTED_DEVICE_STATES_CHANGED_THERMAL_CRITICAL;
import static com.android.server.devicestate.DeviceStateProvider.SUPPORTED_DEVICE_STATES_CHANGED_THERMAL_NORMAL;
import static com.android.server.policy.FoldableDeviceStateProvider.DeviceStatePredicateWrapper.createConfig;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.devicestate.DeviceState;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputSensorInfo;
import android.os.Handler;
import android.os.PowerManager;
import android.testing.AndroidTestingRunner;
import android.view.Display;

import androidx.annotation.NonNull;

import com.android.server.devicestate.DeviceStateProvider.Listener;
import com.android.server.policy.FoldableDeviceStateProvider.DeviceStatePredicateWrapper;
import com.android.server.policy.feature.flags.FakeFeatureFlagsImpl;
import com.android.server.policy.feature.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldSetter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for {@link FoldableDeviceStateProvider}.
 * <p/>
 * Run with <code>atest FoldableDeviceStateProviderTest</code>.
 */
@RunWith(AndroidTestingRunner.class)
public final class FoldableDeviceStateProviderTest {

    private static final Set<Integer> EMPTY_PROPERTY_SET = new HashSet<>();
    private static final Set<Integer> THERMAL_PROPERTY_SET = new HashSet<>(
            Arrays.asList(DeviceState.PROPERTY_EMULATED_ONLY,
                    DeviceState.PROPERTY_POLICY_UNSUPPORTED_WHEN_THERMAL_STATUS_CRITICAL,
                    DeviceState.PROPERTY_POLICY_UNSUPPORTED_WHEN_POWER_SAVE_MODE));

    private final ArgumentCaptor<DeviceState[]> mDeviceStateArrayCaptor =
            ArgumentCaptor.forClass(DeviceState[].class);
    @Captor
    private ArgumentCaptor<Integer> mIntegerCaptor;
    @Captor
    private ArgumentCaptor<DisplayManager.DisplayListener> mDisplayListenerCaptor;
    @Mock
    private SensorManager mSensorManager;
    @Mock
    private Context mContext;
    @Mock
    private InputSensorInfo mInputSensorInfo;
    private Sensor mHallSensor;
    private Sensor mHingeAngleSensor;
    @Mock
    private DisplayManager mDisplayManager;
    private FoldableDeviceStateProvider mProvider;
    @Mock
    private Display mDefaultDisplay;
    @Mock
    private Display mExternalDisplay;

    private final FakeFeatureFlagsImpl mFakeFeatureFlags = new FakeFeatureFlagsImpl();
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mFakeFeatureFlags.setFlag(Flags.FLAG_ENABLE_DUAL_DISPLAY_BLOCKING, true);

        mHallSensor = new Sensor(mInputSensorInfo);
        mHingeAngleSensor = new Sensor(mInputSensorInfo);
    }

    @Test
    public void create_emptyConfiguration_throwsException() {
        assertThrows(IllegalArgumentException.class, this::createProvider);
    }

    @Test
    public void create_duplicatedDeviceStateIdentifiers_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> createProvider(
                        createConfig(createDeviceState(0, "ONE"), (c) -> true),
                        createConfig(createDeviceState(0, "TWO"), (c) -> true)
                ));
    }

    @Test
    public void create_allMatchingStatesDefaultsToTheFirstIdentifier() {
        createProvider(
                createConfig(createDeviceState(1, "ONE"), (c) -> true),
                createConfig(createDeviceState(2, "TWO"), (c) -> true),
                createConfig(createDeviceState(3, "THREE"), (c) -> true)
        );

        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);

        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_INITIALIZED));
        final DeviceState[] expectedStates = new DeviceState[]{
                createDeviceState(1, "ONE", EMPTY_PROPERTY_SET),
                createDeviceState(2, "TWO", EMPTY_PROPERTY_SET),
                createDeviceState(3, "THREE", EMPTY_PROPERTY_SET)
        };
        assertArrayEquals(expectedStates, mDeviceStateArrayCaptor.getValue());

        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(1, mIntegerCaptor.getValue().intValue());
    }

    @Test
    public void create_multipleMatchingStatesDefaultsToTheLowestIdentifier() {
        createProvider(
                createConfig(createDeviceState(1, "ONE"), (c) -> false),
                createConfig(createDeviceState(3, "THREE"), (c) -> false),
                createConfig(createDeviceState(4, "FOUR"), (c) -> true),
                createConfig(createDeviceState(2, "TWO"), (c) -> true)
        );

        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);

        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(2, mIntegerCaptor.getValue().intValue());
    }

    @Test
    public void test_hingeAngleUpdatedFirstTime_switchesToMatchingState() throws Exception {
        createProvider(createConfig(createDeviceState(1, "ONE"),
                        (c) -> c.getHingeAngle() < 90f),
                createConfig(createDeviceState(2, "TWO"),
                        (c) -> c.getHingeAngle() >= 90f));
        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);
        verify(listener, never()).onStateChanged(anyInt());
        clearInvocations(listener);

        sendSensorEvent(mHingeAngleSensor, /* value= */ 100f);

        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(2, mIntegerCaptor.getValue().intValue());
    }

    @Test
    public void test_hallSensorUpdatedFirstTime_switchesToMatchingState() throws Exception {
        createProvider(createConfig(createDeviceState(1, "ONE"),
                        (c) -> !c.isHallSensorClosed()),
                createConfig(createDeviceState(2, "TWO"),
                        FoldableDeviceStateProvider::isHallSensorClosed));
        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);
        verify(listener, never()).onStateChanged(anyInt());
        clearInvocations(listener);

        // Hall sensor value '1f' is for the closed state
        sendSensorEvent(mHallSensor, /* value= */ 1f);

        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(2, mIntegerCaptor.getValue().intValue());
    }

    @Test
    public void test_hingeAngleUpdatedSecondTime_switchesToMatchingState() throws Exception {
        createProvider(createConfig(createDeviceState(1, "ONE"),
                        (c) -> c.getHingeAngle() < 90f),
                createConfig(createDeviceState(2, "TWO"),
                        (c) -> c.getHingeAngle() >= 90f));
        sendSensorEvent(mHingeAngleSensor, /* value= */ 30f);
        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);
        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(1, mIntegerCaptor.getValue().intValue());
        clearInvocations(listener);

        sendSensorEvent(mHingeAngleSensor, /* value= */ 100f);

        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(2, mIntegerCaptor.getValue().intValue());
    }

    @Test
    public void test_hallSensorUpdatedSecondTime_switchesToMatchingState() throws Exception {
        createProvider(createConfig(createDeviceState(1, "ONE"),
                        (c) -> !c.isHallSensorClosed()),
                createConfig(createDeviceState(2, "TWO"),
                        FoldableDeviceStateProvider::isHallSensorClosed));
        sendSensorEvent(mHallSensor, /* value= */ 0f);
        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);
        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(1, mIntegerCaptor.getValue().intValue());
        clearInvocations(listener);

        // Hall sensor value '1f' is for the closed state
        sendSensorEvent(mHallSensor, /* value= */ 1f);

        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(2, mIntegerCaptor.getValue().intValue());
    }

    @Test
    public void test_invalidSensorValues_onStateChangedIsNotTriggered() throws Exception {
        createProvider(createConfig(createDeviceState(1, "ONE"),
                        (c) -> c.getHingeAngle() < 90f),
                createConfig(createDeviceState(2, "TWO"),
                        (c) -> c.getHingeAngle() >= 90f));
        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);
        clearInvocations(listener);

        // First, switch to a non-default state.
        sendSensorEvent(mHingeAngleSensor, /* value= */ 100f);
        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(2, mIntegerCaptor.getValue().intValue());

        clearInvocations(listener);

        // Then, send an invalid sensor event, verify that onStateChanged() is not triggered.
        sendInvalidSensorEvent(mHingeAngleSensor);

        verify(listener, never()).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_INITIALIZED));
        verify(listener, never()).onStateChanged(mIntegerCaptor.capture());
    }

    @Test
    public void test_nullSensorValues_noExceptionThrown() throws Exception {
        createProvider(createConfig(createDeviceState(1, "ONE"),
                (c) -> c.getHingeAngle() < 90f));
        sendInvalidSensorEvent(null);
    }

    @Test
    public void test_flagDisableWhenThermalStatusCritical() throws Exception {
        createProvider(createConfig(createDeviceState(1, "CLOSED"),
                        (c) -> c.getHingeAngle() < 5f),
                createConfig(createDeviceState(2, "HALF_OPENED"),
                        (c) -> c.getHingeAngle() < 90f),
                createConfig(createDeviceState(3, "OPENED"),
                        (c) -> c.getHingeAngle() < 180f),
                createConfig(
                        createDeviceState(4, "THERMAL_TEST", THERMAL_PROPERTY_SET),
                        (c) -> true));
        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);
        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_INITIALIZED));
        assertArrayEquals(
                new DeviceState[]{
                        createDeviceState(1, "CLOSED", EMPTY_PROPERTY_SET),
                        createDeviceState(2, "HALF_OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(3, "OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(4, "THERMAL_TEST", THERMAL_PROPERTY_SET)},
                mDeviceStateArrayCaptor.getValue());
        clearInvocations(listener);

        mProvider.onThermalStatusChanged(PowerManager.THERMAL_STATUS_MODERATE);
        verify(listener, never()).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_INITIALIZED));
        clearInvocations(listener);

        // The THERMAL_TEST state should be disabled.
        mProvider.onThermalStatusChanged(PowerManager.THERMAL_STATUS_CRITICAL);
        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_THERMAL_CRITICAL));
        assertArrayEquals(
                new DeviceState[]{
                        createDeviceState(1, "CLOSED", EMPTY_PROPERTY_SET),
                        createDeviceState(2, "HALF_OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(3, "OPENED", EMPTY_PROPERTY_SET)},
                mDeviceStateArrayCaptor.getValue());
        clearInvocations(listener);

        // The THERMAL_TEST state should be re-enabled.
        mProvider.onThermalStatusChanged(PowerManager.THERMAL_STATUS_LIGHT);
        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_THERMAL_NORMAL));
        assertArrayEquals(
                new DeviceState[]{
                        createDeviceState(1, "CLOSED", EMPTY_PROPERTY_SET),
                        createDeviceState(2, "HALF_OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(3, "OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(4, "THERMAL_TEST", THERMAL_PROPERTY_SET)},
                mDeviceStateArrayCaptor.getValue());
    }

    @Test
    public void test_flagDisableWhenPowerSaveEnabled() throws Exception {
        createProvider(createConfig(createDeviceState(1, "CLOSED"),
                        (c) -> c.getHingeAngle() < 5f),
                createConfig(createDeviceState(2, "HALF_OPENED"),
                        (c) -> c.getHingeAngle() < 90f),
                createConfig(createDeviceState(3, "OPENED"),
                        (c) -> c.getHingeAngle() < 180f),
                createConfig(
                        createDeviceState(4, "THERMAL_TEST", THERMAL_PROPERTY_SET),
                        (c) -> true));
        mProvider.onPowerSaveModeChanged(false /* isPowerSaveModeEnabled */);
        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);

        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_INITIALIZED));
        assertArrayEquals(
                new DeviceState[]{
                        createDeviceState(1, "CLOSED", EMPTY_PROPERTY_SET),
                        createDeviceState(2, "HALF_OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(3, "OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(4, "THERMAL_TEST", THERMAL_PROPERTY_SET)},
                mDeviceStateArrayCaptor.getValue());
        clearInvocations(listener);

        mProvider.onPowerSaveModeChanged(false /* isPowerSaveModeEnabled */);
        verify(listener, never()).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_INITIALIZED));
        clearInvocations(listener);

        // The THERMAL_TEST state should be disabled due to power save being enabled.
        mProvider.onPowerSaveModeChanged(true /* isPowerSaveModeEnabled */);
        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_POWER_SAVE_ENABLED));
        assertArrayEquals(
                new DeviceState[]{
                        createDeviceState(1, "CLOSED", EMPTY_PROPERTY_SET),
                        createDeviceState(2, "HALF_OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(3, "OPENED", EMPTY_PROPERTY_SET)},
                mDeviceStateArrayCaptor.getValue());
        clearInvocations(listener);

        // The THERMAL_TEST state should be re-enabled.
        mProvider.onPowerSaveModeChanged(false /* isPowerSaveModeEnabled */);
        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_POWER_SAVE_DISABLED));
        assertArrayEquals(
                new DeviceState[]{
                        createDeviceState(1, "CLOSED", EMPTY_PROPERTY_SET),
                        createDeviceState(2, "HALF_OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(3, "OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(4, "THERMAL_TEST", THERMAL_PROPERTY_SET)},
                mDeviceStateArrayCaptor.getValue());
    }

    @Test
    public void test_previousStateBasedPredicate() {
        // Create a configuration where state TWO could be matched only if
        // the previous state was 'THREE'
        createProvider(
                createConfig(createDeviceState(1, "ONE"),
                        (c) -> c.getHingeAngle() < 30f),
                createConfig(createDeviceState(2, "TWO"),
                        (c) -> c.getLastReportedDeviceState() == 3 && c.getHingeAngle() > 120f),
                createConfig(createDeviceState(3, "THREE"),
                        (c) -> c.getHingeAngle() > 90f)
        );
        sendSensorEvent(mHingeAngleSensor, /* value= */ 0f);
        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);

        // Check that the initial state is 'ONE'
        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(1, mIntegerCaptor.getValue().intValue());
        clearInvocations(listener);

        // Should not match state 'TWO', it should match only state 'THREE'
        // (because the previous state is not 'THREE', it is 'ONE')
        sendSensorEvent(mHingeAngleSensor, /* value= */ 180f);
        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(3, mIntegerCaptor.getValue().intValue());
        clearInvocations(listener);

        // Now it should match state 'TWO'
        // (because the previous state is 'THREE' now)
        sendSensorEvent(mHingeAngleSensor, /* value= */ 180f);
        verify(listener).onStateChanged(mIntegerCaptor.capture());
        assertEquals(2, mIntegerCaptor.getValue().intValue());
    }

    @Test
    public void isScreenOn_afterDisplayChangedToOn_returnsTrue() {
        createProvider(
                createConfig(createDeviceState(1, "ONE"), (c) -> true)
        );

        setScreenOn(true);

        assertThat(mProvider.isScreenOn()).isTrue();
    }

    @Test
    public void isScreenOn_afterDisplayChangedToOff_returnsFalse() {
        createProvider(
                createConfig(createDeviceState(1, "ONE"), (c) -> true)
        );

        setScreenOn(false);

        assertThat(mProvider.isScreenOn()).isFalse();
    }

    @Test
    public void isScreenOn_afterDisplayChangedToOnThenOff_returnsFalse() {
        createProvider(
                createConfig(createDeviceState(1, "ONE"), (c) -> true)
        );

        setScreenOn(true);
        setScreenOn(false);

        assertThat(mProvider.isScreenOn()).isFalse();
    }

    @Test
    public void test_dualScreenDisabledWhenExternalScreenIsConnected() throws Exception {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDefaultDisplay});
        when(mDefaultDisplay.getType()).thenReturn(TYPE_INTERNAL);

        createProvider(createConfig(createDeviceState(1, "CLOSED"),
                        (c) -> c.getHingeAngle() < 5f),
                createConfig(createDeviceState(2, "HALF_OPENED"),
                        (c) -> c.getHingeAngle() < 90f),
                createConfig(createDeviceState(3, "OPENED"),
                        (c) -> c.getHingeAngle() < 180f),
                createConfig(createDeviceState(4, "DUAL_DISPLAY"), (c) -> false,
                        FoldableDeviceStateProvider::hasNoConnectedExternalDisplay));

        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);
        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_INITIALIZED));
        assertThat(mDeviceStateArrayCaptor.getValue()).asList().containsExactly(
                new DeviceState[]{
                        createDeviceState(1, "CLOSED", EMPTY_PROPERTY_SET),
                        createDeviceState(2, "HALF_OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(3, "OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(4, "DUAL_DISPLAY",
                                EMPTY_PROPERTY_SET)}).inOrder();

        clearInvocations(listener);

        when(mDisplayManager.getDisplays())
                .thenReturn(new Display[]{mDefaultDisplay, mExternalDisplay});
        when(mDisplayManager.getDisplay(1)).thenReturn(mExternalDisplay);
        when(mExternalDisplay.getType()).thenReturn(TYPE_EXTERNAL);

        // The DUAL_DISPLAY state should be disabled.
        mProvider.onDisplayAdded(1);
        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_EXTERNAL_DISPLAY_ADDED));
        assertThat(mDeviceStateArrayCaptor.getValue()).asList().containsExactly(
                new DeviceState[]{
                        createDeviceState(1, "CLOSED", EMPTY_PROPERTY_SET),
                        createDeviceState(2, "HALF_OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(3, "OPENED", EMPTY_PROPERTY_SET)}).inOrder();
        clearInvocations(listener);

        // The DUAL_DISPLAY state should be re-enabled.
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDefaultDisplay});
        mProvider.onDisplayRemoved(1);
        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_EXTERNAL_DISPLAY_REMOVED));
        assertThat(mDeviceStateArrayCaptor.getValue()).asList().containsExactly(
                new DeviceState[]{
                        createDeviceState(1, "CLOSED", EMPTY_PROPERTY_SET),
                        createDeviceState(2, "HALF_OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(3, "OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(4, "DUAL_DISPLAY",
                                EMPTY_PROPERTY_SET)}).inOrder();
    }

    @Test
    public void test_notifySupportedStatesChangedCalledOnlyOnInitialExternalScreenAddition() {
        when(mDisplayManager.getDisplays()).thenReturn(new Display[]{mDefaultDisplay});
        when(mDefaultDisplay.getType()).thenReturn(TYPE_INTERNAL);

        createProvider(createConfig(createDeviceState(1, "CLOSED"),
                        (c) -> c.getHingeAngle() < 5f),
                createConfig(createDeviceState(2, "HALF_OPENED"),
                        (c) -> c.getHingeAngle() < 90f),
                createConfig(createDeviceState(3, "OPENED"),
                        (c) -> c.getHingeAngle() < 180f),
                createConfig(createDeviceState(4, "DUAL_DISPLAY"),
                        FoldableDeviceStateProvider::hasNoConnectedExternalDisplay));

        Listener listener = mock(Listener.class);
        mProvider.setListener(listener);
        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_INITIALIZED));
        assertThat(mDeviceStateArrayCaptor.getValue()).asList().containsExactly(
                new DeviceState[]{
                        createDeviceState(1, "CLOSED", EMPTY_PROPERTY_SET),
                        createDeviceState(2, "HALF_OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(3, "OPENED", EMPTY_PROPERTY_SET),
                        createDeviceState(4, "DUAL_DISPLAY",
                                EMPTY_PROPERTY_SET)}).inOrder();

        clearInvocations(listener);

        addExternalDisplay(1);
        verify(listener).onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_EXTERNAL_DISPLAY_ADDED));
        addExternalDisplay(2);
        addExternalDisplay(3);
        addExternalDisplay(4);
        verify(listener, times(1))
                .onSupportedDeviceStatesChanged(mDeviceStateArrayCaptor.capture(),
                eq(SUPPORTED_DEVICE_STATES_CHANGED_EXTERNAL_DISPLAY_ADDED));
    }

    @Test
    public void hasNoConnectedDisplay_afterExternalDisplayAdded_returnsFalse() {
        createProvider(
                createConfig(createDeviceState(1, "ONE", Collections.emptySet()),
                        (c) -> true, FoldableDeviceStateProvider::hasNoConnectedExternalDisplay)
        );

        addExternalDisplay(/* displayId */ 1);

        assertThat(mProvider.hasNoConnectedExternalDisplay()).isFalse();
    }

    @Test
    public void testOnDisplayAddedWithNullDisplayDoesNotThrowNPE() {
        createProvider(
                createConfig(
                        createDeviceState(1, "ONE", Collections.emptySet()),
                        (c) -> true, FoldableDeviceStateProvider::hasNoConnectedExternalDisplay)
        );

        when(mDisplayManager.getDisplay(1)).thenReturn(null);
        // This call should not throw NPE.
        mProvider.onDisplayAdded(1);
    }

    @Test
    public void hasNoConnectedDisplay_afterExternalDisplayAddedAndRemoved_returnsTrue() {
        createProvider(
                createConfig(
                        createDeviceState(1, "ONE", Collections.emptySet()),
                        (c) -> true, FoldableDeviceStateProvider::hasNoConnectedExternalDisplay)
        );

        addExternalDisplay(/* displayId */ 1);
        mProvider.onDisplayRemoved(1);

        assertThat(mProvider.hasNoConnectedExternalDisplay()).isTrue();
    }
    private void addExternalDisplay(int displayId) {
        when(mDisplayManager.getDisplay(displayId)).thenReturn(mExternalDisplay);
        when(mExternalDisplay.getType()).thenReturn(TYPE_EXTERNAL);
        mProvider.onDisplayAdded(displayId);
    }
    private void setScreenOn(boolean isOn) {
        Display mockDisplay = mock(Display.class);
        int state = isOn ? STATE_ON : STATE_OFF;
        when(mockDisplay.getState()).thenReturn(state);
        when(mDisplayManager.getDisplay(eq(DEFAULT_DISPLAY))).thenReturn(mockDisplay);
        mDisplayListenerCaptor.getValue().onDisplayChanged(DEFAULT_DISPLAY);
    }

    private void sendSensorEvent(Sensor sensor, float value) {
        SensorEvent event = mock(SensorEvent.class);
        event.sensor = sensor;
        try {
            FieldSetter.setField(event, event.getClass().getField("values"),
                    new float[]{value});
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        mProvider.onSensorChanged(event);
    }

    private void sendInvalidSensorEvent(Sensor sensor) {
        SensorEvent event = mock(SensorEvent.class);
        event.sensor = sensor;
        try {
            // Set empty values array to make the event invalid
            FieldSetter.setField(event, event.getClass().getField("values"),
                    new float[]{});
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        mProvider.onSensorChanged(event);
    }

    private void createProvider(DeviceStatePredicateWrapper... configurations) {
        mProvider = new FoldableDeviceStateProvider(mFakeFeatureFlags, mContext, mSensorManager,
                mHingeAngleSensor, mHallSensor, mDisplayManager, configurations);
        verify(mDisplayManager)
                .registerDisplayListener(
                        mDisplayListenerCaptor.capture(),
                        nullable(Handler.class));
    }

    /**
     * Returns a new {@link DeviceState} object
     */
    private DeviceState createDeviceState(int identifier,
            @NonNull String name,
            @NonNull Set<@DeviceState.DeviceStateProperties Integer> systemProperties) {
        return new DeviceState(new DeviceState.Configuration.Builder(identifier, name)
                .setSystemProperties(systemProperties)
                .build());
    }

    /**
     * Returns a new {@link DeviceState} object
     */
    private DeviceState createDeviceState(int identifier,
            @NonNull String name) {
        return createDeviceState(identifier, name, Collections.emptySet());
    }
}
