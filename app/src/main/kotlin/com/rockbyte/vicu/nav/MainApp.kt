package com.rockbyte.vicu.nav

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.rockbyte.vicu.page.AudioExportPage
import com.rockbyte.vicu.page.HomePage
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object AudioExportRoute : NavKey

/** Navigation 3 路由与目的地集中注册（AGENTS.md）。 */
@Composable
fun MainApp() {
    val backStack = rememberNavBackStack(HomeRoute)
    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<HomeRoute> { HomePage() }
            entry<AudioExportRoute> { AudioExportPage() }
        },
    )
}
