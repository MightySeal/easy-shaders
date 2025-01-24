package io.easyshaders.legacy

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LegacyCameraViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
) : ViewModel() {

    val gallery: StateFlow<List<LocalPicture>>
        field = MutableStateFlow<List<LocalPicture>>(emptyList())

    fun onTakePhoto(picture: LocalPicture) {
        gallery.value += picture
    }

    data class LocalPicture(
        val bitmap: Bitmap,
        val uri: Uri?,
    )
}
