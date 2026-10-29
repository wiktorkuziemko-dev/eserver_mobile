package pl.fleet.monitor.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.fleet.monitor.AppGraph
import pl.fleet.monitor.data.Credentials

data class LoginUiState(
    val login: String = "",
    val password: String = "",
    val remember: Boolean = true,
    val serverUrl: String = "",
    val showServer: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = login.isNotBlank() && password.isNotEmpty() && !loading
}

class LoginViewModel : ViewModel() {
    private val container = AppGraph.container
    private val session = container.session

    private val _ui = MutableStateFlow(
        LoginUiState(
            login = session.savedLogin,
            password = session.savedPassword,
            remember = session.rememberEnabled,
            serverUrl = session.baseUrl,
        )
    )
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    fun onLogin(v: String) = _ui.update { it.copy(login = v, error = null) }
    fun onPassword(v: String) = _ui.update { it.copy(password = v, error = null) }
    fun onRemember(v: Boolean) = _ui.update { it.copy(remember = v) }
    fun onServer(v: String) = _ui.update { it.copy(serverUrl = v, error = null) }
    fun toggleServer() = _ui.update { it.copy(showServer = !it.showServer) }

    fun submit() {
        val s = _ui.value
        if (!s.canSubmit) return
        val url = s.serverUrl.trim()
        if (url.isNotEmpty() && !url.startsWith("https://")) {
            _ui.update { it.copy(error = "Adres serwera musi zaczynać się od https://") }
            return
        }
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                session.baseUrl = url
                val creds = Credentials.fromPlain(s.login, s.password)
                container.repository.verify(creds)
                session.login(creds, s.password, s.remember)
                _ui.update { it.copy(loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Logowanie nie powiodło się") }
            }
        }
    }
}
