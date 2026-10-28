package pl.fleet.monitor.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(vm: LoginViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.LocalShipping, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            Text("Monitoring floty", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Zaloguj się danymi konta platformy E-SERVER",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = ui.login,
                onValueChange = vm::onLogin,
                label = { Text("Login") },
                leadingIcon = { Icon(Icons.Outlined.Person, null) },
                singleLine = true,
                enabled = !ui.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.password,
                onValueChange = vm::onPassword,
                label = { Text("Hasło") },
                leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (passwordVisible) "Ukryj hasło" else "Pokaż hasło",
                        )
                    }
                },
                singleLine = true,
                enabled = !ui.loading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ui.remember, onCheckedChange = vm::onRemember, enabled = !ui.loading)
                Text("Zapamiętaj mnie", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::toggleServer) {
                    Icon(Icons.Outlined.Dns, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Serwer")
                }
            }

            AnimatedVisibility(ui.showServer) {
                OutlinedTextField(
                    value = ui.serverUrl,
                    onValueChange = vm::onServer,
                    label = { Text("Adres API") },
                    supportingText = { Text("Domyślnie https://e-server.com.pl/api/") },
                    singleLine = true,
                    enabled = !ui.loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }

            ui.error?.let {
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::submit, enabled = ui.canSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (ui.loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Zaloguj się", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Dostęp do API musi być aktywowany na koncie przez operatora platformy.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
