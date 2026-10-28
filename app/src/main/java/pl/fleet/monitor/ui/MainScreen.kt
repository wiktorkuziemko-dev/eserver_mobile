package pl.fleet.monitor.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import pl.fleet.monitor.ui.list.VehicleListScreen
import pl.fleet.monitor.ui.map.MapScreen

@Composable
fun MainScreen(vm: FleetViewModel, onOpenVehicle: (String) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.autoRefreshLoop() }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = ui.tab == MainTab.MAP, onClick = { vm.setTab(MainTab.MAP) },
                    icon = { Icon(Icons.Outlined.Map, null) }, label = { Text("Mapa") },
                )
                NavigationBarItem(
                    selected = ui.tab == MainTab.LIST, onClick = { vm.setTab(MainTab.LIST) },
                    icon = { Icon(Icons.AutoMirrored.Outlined.List, null) }, label = { Text("Pojazdy") },
                )
                NavigationBarItem(
                    selected = ui.tab == MainTab.SETTINGS, onClick = { vm.setTab(MainTab.SETTINGS) },
                    icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Ustawienia") },
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (ui.tab) {
            MainTab.MAP -> MapScreen(vm, ui, onOpenVehicle, modifier)
            MainTab.LIST -> VehicleListScreen(vm, ui, onOpenVehicle, modifier)
            MainTab.SETTINGS -> SettingsScreen(vm, modifier)
        }
    }
}
