package io.easyshaders.data.processor

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.concurrent.futures.CallbackToFutureAdapter

// https://github.com/A-Star100/A-Star100-AUG2-2024/blob/a041b416cc0b599dfee86ef1224aa846ce1a4093/DoodleNow-SRC/DoodleNow%20Decompiled/sources/androidx/camera/core/processing/AutoValue_DefaultSurfaceProcessor_PendingSnapshot.java#L6

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class AutoValueDefaultSurfaceProcessorPendingSnapshot(
    /* access modifiers changed from: package-private */
    override val jpegQuality: Int,
    /* access modifiers changed from: package-private */
    override val rotationDegrees: Int,
    completer: CallbackToFutureAdapter.Completer<Void?>
) : DefaultSurfaceProcessor.PendingSnapshot() {

    /* access modifiers changed from: package-private */
    override var completer: CallbackToFutureAdapter.Completer<Void?>? = completer

    override fun toString() =
        "PendingSnapshot{jpegQuality=" +
                this.jpegQuality + ", rotationDegrees=" +
                this.rotationDegrees + ", completer=" +
                this.completer + "}"

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is DefaultSurfaceProcessor.PendingSnapshot) {
            return false
        }
        val pendingSnapshot = other as DefaultSurfaceProcessor.PendingSnapshot
        return this.jpegQuality ==
                pendingSnapshot.jpegQuality
                && this.rotationDegrees == pendingSnapshot.rotationDegrees
                && this.completer == pendingSnapshot.completer
    }

    override fun hashCode() =
        ((((this.jpegQuality xor 1000003) * 1000003) xor this.rotationDegrees) * 1000003) xor completer.hashCode()
}
