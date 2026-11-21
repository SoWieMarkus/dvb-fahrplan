package markus.wieland.dvbfahrplan

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import markus.wieland.dvbfahrplan.core.designsystem.FahrplanTheme
import markus.wieland.dvbfahrplan.core.model.Departure
import markus.wieland.dvbfahrplan.core.model.Route
import markus.wieland.dvbfahrplan.core.model.Stop
import markus.wieland.dvbfahrplan.feature.detail.RouteDetailScreen
import markus.wieland.dvbfahrplan.feature.detail.TripScreen
import markus.wieland.dvbfahrplan.feature.detail.TripViewModel
import markus.wieland.dvbfahrplan.feature.disruptions.DisruptionsScreen
import markus.wieland.dvbfahrplan.feature.search.SearchScreen

/** Screens of the app. Detail keys carry their data in memory; the search screen is the root. */
sealed interface Screen {
    data object Search : Screen
    data class RouteDetail(val route: Route, val origin: Stop, val destination: Stop) : Screen
    data class TripDetail(val departure: Departure, val stop: Stop) : Screen
    /** [ids] null = all current service changes; otherwise only these, about [subtitle] (e.g. a stop). */
    data class Disruptions(val ids: List<String>? = null, val subtitle: String? = null) : Screen
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            FahrplanTheme(dark = dark) {
                App(onExit = ::finish)
            }
        }
    }
}

@Composable
private fun App(onExit: () -> Unit) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Search) }
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onExit() }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { (slideInHorizontally { it / 5 } + fadeIn()) togetherWith fadeOut() },
        popTransitionSpec = { fadeIn() togetherWith (slideOutHorizontally { it / 5 } + fadeOut()) },
        predictivePopTransitionSpec = { fadeIn() togetherWith (slideOutHorizontally { it / 5 } + fadeOut()) },
        entryProvider = entryProvider {
            entry<Screen.Search> {
                SearchScreen(
                    viewModel = hiltViewModel(),
                    onOpenRoute = { route, origin, destination -> backStack.add(Screen.RouteDetail(route, origin, destination)) },
                    onOpenTrip = { departure, stop -> backStack.add(Screen.TripDetail(departure, stop)) },
                    onOpenDisruptions = { ids, subtitle -> backStack.add(Screen.Disruptions(ids, subtitle)) },
                    onExit = onExit,
                )
            }
            entry<Screen.RouteDetail> { key ->
                RouteDetailScreen(key.route, key.origin, key.destination, onBack = back)
            }
            entry<Screen.TripDetail> { key ->
                val viewModel = hiltViewModel<TripViewModel, TripViewModel.Factory>(
                    creationCallback = { factory -> factory.create(key.departure, key.stop) },
                )
                TripScreen(viewModel, key.departure, key.stop, onBack = back)
            }
            entry<Screen.Disruptions> { key ->
                DisruptionsScreen(hiltViewModel(), key.ids, key.subtitle, onBack = back)
            }
        },
    )
}
