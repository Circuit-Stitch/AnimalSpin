package casa.falconer.toys

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import casa.falconer.toys.ui.main.MainScreen
import casa.falconer.toys.ui.main.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "main") {
                    composable("main") {
                        MainScreen(onSettings = { nav.navigate("settings") })
                    }
                    composable("settings") { entry ->
                        // Guard against the double-pop that empties the back stack and shows a
                        // blank white screen: a quick double-tap of Save fires popBackStack twice.
                        // After the first pop the entry is no longer RESUMED, so the second is a no-op.
                        SettingsScreen(onDone = {
                            if (entry.lifecycleIsResumed()) nav.popBackStack()
                        })
                    }
                }
            }
        }
    }
}

private fun NavBackStackEntry.lifecycleIsResumed() =
    lifecycle.currentState == Lifecycle.State.RESUMED
