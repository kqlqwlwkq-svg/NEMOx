package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.data.GamingRemoteRepository
import com.example.data.LocalAppPreferences
import com.example.data.NemoxFirestoreRepository
import com.example.data.SettingsDataStore
import com.example.notifications.NemoxNotificationHelper
import com.example.ui.screens.MainShellScreen
import com.example.ui.theme.NEMOxTheme
import com.example.viewmodel.NemoxViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NemoxNotificationHelper.ensureChannels(this)
        NemoxNotificationHelper.schedulePeriodicSync(this)

        setContent {
            val context = LocalContext.current
            val dataStore = remember(context) { SettingsDataStore(context.applicationContext) }
            val localPrefs by dataStore.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = LocalAppPreferences()
            )
            val scope = rememberCoroutineScope()

            NEMOxTheme(
                darkTheme = localPrefs.darkMode,
                languageCode = localPrefs.languageCode
            ) {
                AppNavigation(
                    dataStore = dataStore,
                    currentLanguage = localPrefs.languageCode,
                    darkMode = localPrefs.darkMode,
                    onPreAuthLanguageSelect = { code ->
                        scope.launch { dataStore.setLanguage(code) }
                    }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    dataStore: SettingsDataStore,
    currentLanguage: String,
    darkMode: Boolean,
    onPreAuthLanguageSelect: (String) -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val context = LocalContext.current
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NemoxNotificationHelper.hasNotificationPermission(context)
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            currentLanguage = currentLanguage,
            onLanguageSelected = onPreAuthLanguageSelect,
            onAuthSuccess = {}
        )
    } else {
        val uid = user.uid
        key(uid) {
            val nemoxViewModel: NemoxViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        NemoxViewModel(
                            firestoreRepo = NemoxFirestoreRepository(db, auth),
                            remoteRepo = GamingRemoteRepository(),
                            settingsDataStore = dataStore,
                            currentUserId = uid
                        )
                    }
                }
            )

            MainShellScreen(
                viewModel = nemoxViewModel,
                currentLanguage = currentLanguage,
                darkMode = darkMode
            )
        }
    }
}
