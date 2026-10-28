package pl.fleet.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import pl.fleet.monitor.ui.theme.FleetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FleetTheme { AppNavigation() } }
    }
}

private object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val VEHICLE = "vehicle/{id}"
    const val HISTORY = "history/{id}"
    fun vehicle(id: String) = "vehicle/$id"
    fun history(id: String) = "history/$id"
}

@Composable
private fun AppNavigation() {
    val app = LocalContext.current.applicationContext as FleetApp
    val nav = rememberNavController()
    val fleetVm: FleetViewModel = viewModel()
    val credentials by app.session.credentials.collectAsStateWithLifecycle()
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
        composable(Routes.LOGIN) { LoginScreen(viewModel()) }

        composable(Routes.MAIN) {
            MainScreen(fleetVm, onOpenVehicle = { nav.navigate(Routes.vehicle(it)) })
        }

        composable(Routes.VEHICLE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vehicle = fleet.vehicles.firstOrNull { it.deviceId == id }
            if (vehicle == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nie znaleziono pojazdu") }
            } else {
                val vm: VehicleDetailsViewModel = viewModel(key = "details-$id") { VehicleDetailsViewModel(app, vehicle.serial) }
                VehicleDetailsScreen(
                    vehicle = vehicle,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onHistory = { nav.navigate(Routes.history(id)) },
                    onShowOnMap = {
                        fleetVm.showOnMap(id)
                        nav.popBackStack(Routes.MAIN, inclusive = false)
                    },
                    onRefresh = fleetVm::refresh,
                )
            }
        }

        composable(Routes.HISTORY, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vehicle = fleet.vehicles.firstOrNull { it.deviceId == id }
            val vm: HistoryViewModel = viewModel(key = "history-$id") { HistoryViewModel(app, vehicle?.serial.orEmpty()) }
            HistoryScreen(vehicleLabel = vehicle?.label.orEmpty(), vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
