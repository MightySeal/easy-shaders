/*
 * Copyright 2024 The Android Open Source Project
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

package io.easyshaders.lib.processing.util

enum class InputFormat {
    /**
     * Input texture format is unknown.
     *
     * <p>When the input format is unknown, HDR content may require rendering blank frames
     * since we are not sure what type of sampler can be used. For SDR content, it is
     * typically safe to use samplerExternalOES since this can handle both RGB and YUV inputs
     * for SDR content.
     */
    UNKNOWN,
    /**
     * Input texture format is the default format.
     *
     * <p>The texture format may be RGB or YUV. For SDR content, using samplerExternalOES is
     * safe since it will be able to convert YUV to RGB automatically within the shader. For
     * HDR content, the input is expected to be RGB.
     */
    DEFAULT,
    /**
     * Input format is explicitly YUV.
     *
     * <p>This needs to be specified for HDR content. Only __samplerExternal2DY2YEXT should be
     * used for HDR YUV content as samplerExternalOES may not correctly convert to RGB.
     */
    YUV
}