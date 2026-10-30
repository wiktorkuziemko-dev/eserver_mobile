package pl.fleet.monitor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.fleet.monitor.ui.FleetViewModel
import pl.fleet.monitor.ui.MainScreen
import pl.fleet.monitor.ui.details.VehicleDetailsScreen
import pl.fleet.monitor.ui.details.VehicleDetailsViewModel
import pl.fleet.monitor.ui.history.HistoryScreen
import pl.fleet.monitor.ui.history.HistoryViewModel
import pl.fleet.monitor.ui.login.LoginScreen
import pl.fleet.monitor.ui.login.LoginViewModel
import pl.fleet.monitor.ui.settings.MapVisibilityScreen
import pl.fleet.monitor.ui.theme.FleetTheme

/** Główny komponent aplikacji – wspólny dla Androida i iOS. */
@Composable
fun App() {
    FleetTheme { AppNavigation() }
}

private object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val VISIBILITY = "visibility"
    const val VEHICLE = "vehicle/{id}"
    const val HISTORY = "history/{id}?serial={serial}"
    fun vehicle(id: String) = "vehicle/$id"
    fun history(id: String, serial: String) = "history/$id?serial=$serial"
}

@Composable
private fun AppNavigation() {
    val container = AppGraph.container
    val nav = rememberNavController()
    val fleetVm: FleetViewModel = viewModel { FleetViewModel() }
    val credentials by container.session.credentials.collectAsStateWithLifecycle()
    val fleet by fleetVm.ui.collectAsStateWithLifecycle()

    // Zmiana stanu sesji (zalogowanie / wylogowanie) przełącza graf nawigacji.
    LaunchedEffect(credentials == null) {
        val target = if (credentials == null) Routes.LOGIN else Routes.MAIN
        if (nav.currentDestination?.route != target) {
            nav.navigate(target) { popUpTo(nav.graph.id) { inclusive = true } }
        }
        // Np. po odtworzeniu procesu na ekranie szczegółów – lista pojazdów musi zostać pobrana.
        if (credentials != null && fleetVm.ui.value.vehicles.isEmpty()) fleetVm.refresh()
    }

    NavHost(nav, startDestination = if (credentials == null) Routes.LOGIN else Routes.MAIN) {
        composable(Routes.LOGIN) { LoginScreen(viewModel { LoginViewModel() }) }

        composable(Routes.MAIN) {
            MainScreen(
                fleetVm,
                onOpenVehicle = { nav.navigate(Routes.vehicle(it)) },
                onOpenVisibility = { nav.navigate(Routes.VISIBILITY) },
            )
        }

        composable(Routes.VISIBILITY) {
            MapVisibilityScreen(fleetVm, onBack = { nav.popBackStack() })
        }

        composable(Routes.VEHICLE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.savedStateHandle.get<String>("id").orEmpty()
            val vehicle = fleet.vehicles.firstOrNull { it.deviceId == id }
            if (vehicle == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nie znaleziono pojazdu") }
            } else {
                val vm: VehicleDetailsViewModel = viewModel(key = "details-$id") { VehicleDetailsViewModel(vehicle.serial) }
                VehicleDetailsScreen(
                    vehicle = vehicle,
                    icon = fleet.icons[id] ?: pl.fleet.monitor.ui.VehicleIcon.DEFAULT,
                    customLabel = fleet.customLabels[id],
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onHistory = { nav.navigate(Routes.history(id, vehicle.serial)) },
                    onRefresh = fleetVm::refresh,
                    onShowOnMap = {
                        fleetVm.showOnMap(id)
                        nav.popBackStack(Routes.MAIN, inclusive = false)
                    },
                )
            }
        }

        composable(
            Routes.HISTORY,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("serial") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val id = entry.savedStateHandle.get<String>("id").orEmpty()
            val serial = entry.savedStateHandle.get<String>("serial").orEmpty()
            val label = fleet.vehicles.firstOrNull { it.deviceId == id }?.label.orEmpty()
            val vm: HistoryViewModel = viewModel(key = "history-$id") { HistoryViewModel(serial) }
            HistoryScreen(vehicleLabel = label, vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
