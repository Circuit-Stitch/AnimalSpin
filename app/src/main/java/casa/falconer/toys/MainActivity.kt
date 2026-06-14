package casa.falconer.toys

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
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
                    composable("settings") {
                        SettingsScreen(onDone = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
