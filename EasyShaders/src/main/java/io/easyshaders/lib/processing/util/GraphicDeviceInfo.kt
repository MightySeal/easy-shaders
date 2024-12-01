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

data class GraphicDeviceInfo(
    val glVersion: String, // Returns [GLUtils.VERSION_UNKNOWN] if version information can't be retrieved.
    val eglVersion: String, // Returns [GLUtils.VERSION_UNKNOWN] if version information can't be retrieved.
    val glExtensions: String,
    val eglExtensions: String,
) {
    class Builder() {
        private var glVersion: String = GLUtils.VERSION_UNKNOWN
        private var eglVersion: String = GLUtils.VERSION_UNKNOWN
        private var glExtensions: String = ""
        private var eglExtensions: String = ""

        fun setGlVersion(version: String): Builder {
            glVersion = version
            return this
        }

        fun setEglVersion(version: String): Builder {
            eglVersion = version
            return this
        }

        fun setGlExtensions(extensions: String): Builder {
            glExtensions = extensions
            return this
        }

        fun setEglExtensions(extensions: String): Builder {
            eglExtensions = extensions
            return this
        }

        fun build(): GraphicDeviceInfo {
            return GraphicDeviceInfo(glVersion, eglVersion, glExtensions, eglExtensions)
        }
    }
}

