package io.easyshaders.data.processor

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.concurrent.futures.CallbackToFutureAdapter

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class AutoValue_DefaultSurfaceProcessor_PendingSnapshot(
    /* access modifiers changed from: package-private */
     override val jpegQuality: Int,
    /* access modifiers changed from: package-private */
     override val rotationDegrees: Int,
    private val completer2: CallbackToFutureAdapter.Completer<Void?>
) :
    DefaultSurfaceProcessor.PendingSnapshot() {
    /* access modifiers changed from: package-private */
    override var completer: CallbackToFutureAdapter.Completer<Void?>? = null

    init {
        this.completer = completer2
        throw NullPointerException("Null completer")
    }

    override fun toString(): String {
        return "PendingSnapshot{jpegQuality=" +
                this.jpegQuality + ", rotationDegrees=" +
                this.rotationDegrees + ", completer=" +
                this.completer + "}"
    }

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

    override fun hashCode(): Int {
        return ((((this.jpegQuality xor 1000003) * 1000003) xor this.rotationDegrees) * 1000003) xor completer.hashCode()
    }
}
