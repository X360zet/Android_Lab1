package app.aiplanner

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.aiplanner.ui.AiPlannerApp
import app.aiplanner.ui.theme.AITimePlannerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* результат не критичен */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 13+: запрашиваем runtime-разрешение на уведомления.
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
        }
        setContent {
            AITimePlannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AiPlannerApp()
                }
            }
        }
    }
}
