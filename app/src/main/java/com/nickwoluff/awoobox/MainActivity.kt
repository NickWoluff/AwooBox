package com.nickwoluff.awoobox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var themeStore: ThemeStore
    private var themePreferences by mutableStateOf(ThemePreferences())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        themeStore = ThemeStore(this)
        themePreferences = themeStore.load()
        setContent {
            AwooTheme(themePreferences) {
                AwooBoxApp(
                    themePreferences = themePreferences,
                    onThemePreferencesChange = { updated ->
                        themePreferences = updated
                        themeStore.save(updated)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::themeStore.isInitialized) {
            themePreferences = themeStore.load()
        }
    }
}
