package com.example.mymeetings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.mymeetings.ui.details.DetailsScreen
import com.example.mymeetings.ui.edit.EditScreen
import com.example.mymeetings.ui.home.HomeScreen
import com.example.mymeetings.ui.scanner.ScannerScreen
import com.example.mymeetings.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)

    androidx.compose.runtime.LaunchedEffect(MainActivity.initialDestination) {
        val dest = MainActivity.initialDestination
        if (dest != Main) {
            backStack.add(dest)
            MainActivity.initialDestination = Main
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                HomeScreen(
                    onNavigateToScanner = { backStack.add(Scanner) },
                    onNavigateToDetails = { id -> backStack.add(Details(id)) },
                    onNavigateToSettings = { backStack.add(Settings) },
                    viewModel = hiltViewModel(),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            entry<Scanner> {
                ScannerScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToDetails = { id ->
                        // Replaces scanner with details on success
                        backStack.removeLastOrNull()
                        backStack.add(Details(id))
                    },
                    viewModel = hiltViewModel()
                )
            }
            entry<Details> { key ->
                DetailsScreen(
                    meetingId = key.meetingId,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToEdit = { id -> backStack.add(Edit(id)) },
                    viewModel = hiltViewModel()
                )
            }
            entry<Edit> { key ->
                EditScreen(
                    meetingId = key.meetingId,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    viewModel = hiltViewModel()
                )
            }
            entry<Settings> {
                SettingsScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    viewModel = hiltViewModel()
                )
            }
        }
    )
}
