/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.companion.virtual.camera;

import android.annotation.NonNull;
import android.companion.virtual.camera.IVirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtual.camera.VirtualCameraStreamConfig;
import android.companion.virtualcamera.Format;
import android.companion.virtualcamera.IVirtualCameraService;
import android.companion.virtualcamera.SupportedStreamConfiguration;
import android.companion.virtualcamera.VirtualCameraConfiguration;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.os.RemoteException;
import android.view.Surface;

/** Utilities to convert the client side classes to the virtual camera service ones. */
public final class VirtualCameraConversionUtil {

    /**
     * Fetches the configuration of the provided virtual cameraConfig that was provided by its owner
     * and convert it into the {@link IVirtualCameraService} types: {@link
     * VirtualCameraConfiguration}.
     *
     * @param cameraConfig The cameraConfig sent by the client.
     * @return The converted configuration to be sent to the {@link IVirtualCameraService}.
     * @throws RemoteException If there was an issue fetching the configuration from the client.
     */
    @NonNull
    public static android.companion.virtualcamera.VirtualCameraConfiguration
            getServiceCameraConfiguration(@NonNull VirtualCameraConfig cameraConfig)
                    throws RemoteException {
        VirtualCameraConfiguration serviceConfiguration = new VirtualCameraConfiguration();
        serviceConfiguration.supportedStreamConfigs =
                cameraConfig.getStreamConfigs().stream()
                        .map(VirtualCameraConversionUtil::convertSupportedStreamConfiguration)
                        .toArray(SupportedStreamConfiguration[]::new);
        serviceConfiguration.sensorOrientation = cameraConfig.getSensorOrientation();
        serviceConfiguration.lensFacing = cameraConfig.getLensFacing();
        serviceConfiguration.virtualCameraCallback = convertCallback(cameraConfig.getCallback());
        return serviceConfiguration;
    }

    @NonNull
    private static android.companion.virtualcamera.IVirtualCameraCallback convertCallback(
            @NonNull IVirtualCameraCallback camera) {
        return new android.companion.virtualcamera.IVirtualCameraCallback.Stub() {
            @Override
            public void onStreamConfigured(int streamId, Surface surface, int width, int height,
                    int format) throws RemoteException {
                camera.onStreamConfigured(streamId, surface, width, height,
                        convertToJavaFormat(format));
            }

            @Override
            public void onProcessCaptureRequest(int streamId, int frameId) throws RemoteException {
                camera.onProcessCaptureRequest(streamId, frameId);
            }

            @Override
            public void onStreamClosed(int streamId) throws RemoteException {
                camera.onStreamClosed(streamId);
            }
        };
    }

    @NonNull
    private static SupportedStreamConfiguration convertSupportedStreamConfiguration(
            VirtualCameraStreamConfig stream) {
        SupportedStreamConfiguration supportedConfig = new SupportedStreamConfiguration();
        supportedConfig.height = stream.getHeight();
        supportedConfig.width = stream.getWidth();
        supportedConfig.pixelFormat = convertToHalFormat(stream.getFormat());
        supportedConfig.maxFps = stream.getMaximumFramesPerSecond();
        return supportedConfig;
    }

    private static int convertToHalFormat(int javaFormat) {
        return switch (javaFormat) {
            case ImageFormat.YUV_420_888 -> Format.YUV_420_888;
            case PixelFormat.RGBA_8888 -> Format.RGBA_8888;
            default -> Format.UNKNOWN;
        };
    }

    private static int convertToJavaFormat(int halFormat) {
        return switch (halFormat) {
            case Format.YUV_420_888 -> ImageFormat.YUV_420_888;
            case Format.RGBA_8888 -> PixelFormat.RGBA_8888;
            default -> ImageFormat.UNKNOWN;
        };
    }

    private VirtualCameraConversionUtil() {
    }
}
