package io.easyshaders.legacy

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LegacyCameraViewModel @Inject constructor(
    @ApplicationContext private val application: Context
) : ViewModel() {

    private val _gallery = MutableStateFlow<List<LocalPicture>>(emptyList())
    val gallery = _gallery.asStateFlow()

    fun onTakePhoto(picture: LocalPicture) {
        _gallery.value += picture
    }

    data class LocalPicture(
        val bitmap: Bitmap,
        val uri: Uri?,
    )
}
