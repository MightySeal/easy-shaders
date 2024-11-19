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

import androidx.annotation.RestrictTo
import com.google.auto.value.AutoValue

/**
 * Information about an initialized graphics device.
 *
 *
 * This information can be used to determine which version or extensions of OpenGL and EGL
 * are supported on the device to ensure the attached output surface will have expected
 * characteristics.
 */
@AutoValue
abstract class GraphicDeviceInfo  // Should not be instantiated directly
internal constructor() {
    /**
     * Returns the OpenGL version this graphics device has been initialized to.
     *
     *
     * The version is in the form &lt;major&gt;.&lt;minor&gt;.
     *
     *
     * Returns [GLUtils.VERSION_UNKNOWN] if version information can't be
     * retrieved.
     */
    abstract fun getGlVersion(): String

    /**
     * Returns the EGL version this graphics device has been initialized to.
     *
     *
     * The version is in the form &lt;major&gt;.&lt;minor&gt;.
     *
     *
     * Returns [GLUtils.VERSION_UNKNOWN] if version information can't be
     * retrieved.
     */
    abstract fun getEglVersion(): String

    /**
     * Returns a space separated list of OpenGL extensions or an empty string if extensions
     * could not be retrieved.
     */
    abstract fun getGlExtensions(): String

    /**
     * Returns a space separated list of EGL extensions or an empty string if extensions
     * could not be retrieved.
     */
    abstract fun getEglExtensions(): String

    /**
     * Builder for [GraphicDeviceInfo].
     */
    @AutoValue.Builder
    abstract class Builder {
        /**
         * Sets the gl version.
         */
        abstract fun setGlVersion(version: String): Builder

        /**
         * Sets the egl version.
         */
        abstract fun setEglVersion(version: String): Builder

        /**
         * Sets the gl extensions.
         */
        abstract fun setGlExtensions(extensions: String): Builder

        /**
         * Sets the egl extensions.
         */
        abstract fun setEglExtensions(extensions: String): Builder

        /**
         * Builds the [GraphicDeviceInfo].
         */
        abstract fun build(): GraphicDeviceInfo
    }

    companion object {
        /**
         * Returns the Builder.
         */
        fun builder(): Builder {
            return AutoValue_GraphicDeviceInfo.Builder()
                .setGlVersion(GLUtils.VERSION_UNKNOWN)
                .setEglVersion(GLUtils.VERSION_UNKNOWN)
                .setGlExtensions("")
                .setEglExtensions("")
        }
    }
}
