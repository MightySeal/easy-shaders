package io.easyshaders.lib.processing.util

import androidx.camera.core.DynamicRange

val DynamicRange.is10BitHdrBackport: Boolean
    get() = this.isFullySpecifiedBackport
            && encoding != DynamicRange.ENCODING_SDR
            && bitDepth == DynamicRange.BIT_DEPTH_10_BIT


val DynamicRange.isFullySpecifiedBackport: Boolean
    get() = encoding != DynamicRange.ENCODING_UNSPECIFIED
            && encoding != DynamicRange.ENCODING_HDR_UNSPECIFIED
            && bitDepth != DynamicRange.BIT_DEPTH_UNSPECIFIED;

