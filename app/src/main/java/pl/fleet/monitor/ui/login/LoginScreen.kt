package pl.fleet.monitor.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pl.fleet.monitor.R
import pl.fleet.monitor.ui.components.GradientButton
import pl.fleet.monitor.ui.components.StatusBarIcons
import pl.fleet.monitor.ui.theme.BrandGradient
import pl.fleet.monitor.ui.theme.SoftBackground

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(vm: LoginViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val keyboardOpen = WindowInsets.isImeVisible
    val buttonRequester = remember { BringIntoViewRequester() }

    // Po otwarciu klawiatury przewijamy tak, aby przycisk „Zaloguj się” był widoczny nad nią.
    LaunchedEffect(keyboardOpen) {
        if (keyboardOpen) {
            delay(250)
            buttonRequester.bringIntoView()
        }
    }

    StatusBarIcons(white = true)
    Box(Modifier.fillMaxSize().background(SoftBackground)) {
        // Nagłówek z gradientem marki – niższy niż wcześniej, żeby formularz był wyżej
        Box(
            Modifier.fillMaxWidth().height(220.dp)
                .background(BrandGradient, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)),
        )

        Column(Modifier.fillMaxSize().imePadding()) {
          Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo obok nazwy – jeden zwarty wiersz
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(60.dp).background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(72.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("E-SERVER mini", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                        AnimatedVisibility(!keyboardOpen) {
                            Text(
                                "Monitoring floty w czasie rzeczywistym",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(8.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
                        Text(
                            "Zaloguj się danymi konta E-SERVER",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
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
                        Spacer(Modifier.height(10.dp))
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
                            Text("Zapamiętaj dane logowania", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            ) {
                                Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        GradientButton(
                            onClick = vm::submit,
                            enabled = ui.canSubmit,
                            modifier = Modifier.fillMaxWidth().height(52.dp).bringIntoViewRequester(buttonRequester),
                        ) {
                            if (ui.loading) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text("Zaloguj się", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Dostęp do API musi być aktywowany na koncie przez operatora platformy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
          }
          // Stopka z falami i opisem aplikacji – chowa się, gdy otwarta jest klawiatura
          AnimatedVisibility(!keyboardOpen) { WavesFooter() }
        }
    }
}

/**
 * Stopka ekranu logowania: miękkie fale w kolorze marki (nawiązanie do zaokrąglonego nagłówka)
 * i krótki opis aplikacji z ikoną informacji.
 */
@Composable
private fun WavesFooter() {
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val brand = Brush.linearGradient(listOf(Color(0xFFEF5350), Color(0xFFA31515)), Offset.Zero, Offset(w, h))
            fun wave(top: Float, c1: Float, c2: Float, end: Float) = Path().apply {
                moveTo(0f, h * top)
                cubicTo(w * 0.25f, h * c1, w * 0.55f, h * c2, w, h * end)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(wave(0.10f, -0.05f, 0.30f, 0.02f), brand, alpha = 0.07f)
            drawPath(wave(0.34f, 0.18f, 0.52f, 0.26f), brand, alpha = 0.12f)
            // Pełny pas u dołu – sięga pod pasek nawigacji
            val strip = 14.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(0f, h - strip - 6.dp.toPx())
                    cubicTo(w * 0.35f, h - strip + 4.dp.toPx(), w * 0.65f, h - strip - 12.dp.toPx(), w, h - strip - 2.dp.toPx())
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                },
                brand,
            )
        }
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 34.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info, null,
                Modifier.padding(top = 1.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "E-SERVER mini to lekka i nowoczesna mobilna wersja webowej aplikacji E-SERVER do szybkiego " +
                    "podglądu bieżącej pozycji Twoich pojazdów i statystyk na telefonie. Jeżeli szukasz " +
                    "zaawansowanych funkcjonalności, skorzystaj z webowej wersji platformy.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3F4045),
            )
        }
    }
}
