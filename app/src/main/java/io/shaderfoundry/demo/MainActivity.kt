package io.shaderfoundry.demo

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.shaderfoundry.demo.legacy.LegacyCameraScreen
import io.shaderfoundry.demo.theme.ShaderFoundryTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!hasRequiredPermissions()) {
            requestPermissions(
                this, CAMERAX_PERMISSIONS, 0
            )
        }
        setContent {
            ShaderFoundryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LegacyCameraScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return CAMERAX_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    ShaderFoundryTheme {
        LegacyCameraScreen(Modifier)
    }
}
