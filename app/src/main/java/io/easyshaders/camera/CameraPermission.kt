@file:OptIn(ExperimentalPermissionsApi::class)

package io.easyshaders.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState

@Composable
fun CameraPermission(
    permissionState: PermissionState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(permissionState) {
        permissionState.launchPermissionRequest()
    }
}