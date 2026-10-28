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
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
        // Grafika w dolnej części ekranu (pod klawiaturą, gdy jest otwarta)
        RouteIllustration(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(300.dp))

        // Nagłówek z gradientem marki – niższy niż wcześniej, żeby formularz był wyżej
        Box(
            Modifier.fillMaxWidth().height(220.dp)
                .background(BrandGradient, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)),
        )

        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
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
                        Text("E-SERVER PRO", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
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
    }
}

/**
 * Dekoracja dolnej części ekranu: delikatna siatka ulic, trasa przerywaną linią
 * w kolorze marki i pinezki na przystankach. Górna krawędź płynnie zanika w tło.
 */
@Composable
private fun RouteIllustration(modifier: Modifier = Modifier) {
    val street = Color(0xFFE2E4E8)
    val streetMajor = Color(0xFFD9DCE1)
    val route = Color(0xFFD32F2F)
    val fadeTo = Color(0xFFF7F7F8)
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // Siatka ulic (lekko skośna) i dwie główne arterie
        val step = 46.dp.toPx()
        var x = -h
        while (x < w + h) {
            drawLine(street, Offset(x, h), Offset(x + h * 0.35f, 0f), strokeWidth = 2.dp.toPx())
            x += step
        }
        var y = h * 0.15f
        while (y < h) {
            drawLine(street, Offset(0f, y + 18.dp.toPx()), Offset(w, y - 10.dp.toPx()), strokeWidth = 2.dp.toPx())
            y += step * 0.8f
        }
        drawLine(streetMajor, Offset(0f, h * 0.82f), Offset(w, h * 0.55f), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(streetMajor, Offset(w * 0.62f, h), Offset(w * 0.78f, 0f), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)

        // Trasa
        val p = Path().apply {
            moveTo(w * 0.08f, h * 0.92f)
            cubicTo(w * 0.28f, h * 0.70f, w * 0.30f, h * 0.48f, w * 0.48f, h * 0.52f)
            cubicTo(w * 0.66f, h * 0.56f, w * 0.70f, h * 0.30f, w * 0.90f, h * 0.36f)
        }
        drawPath(p, route.copy(alpha = 0.18f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
        drawPath(
            p, route.copy(alpha = 0.75f),
            style = Stroke(
                width = 3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
            ),
        )
        drawStop(Offset(w * 0.08f, h * 0.92f), Color(0xFF2E9E44))
        drawStop(Offset(w * 0.48f, h * 0.52f), Color(0xFFF59300))
        drawPin(Offset(w * 0.90f, h * 0.36f), route)

        // Zanikanie u góry – grafika łagodnie wtapia się w tło
        drawRect(Brush.verticalGradient(0f to fadeTo, 0.45f to fadeTo.copy(alpha = 0f), startY = 0f, endY = h))
    }
}

private fun DrawScope.drawStop(c: Offset, color: Color) {
    drawCircle(Color.White, radius = 9.dp.toPx(), center = c)
    drawCircle(color, radius = 6.dp.toPx(), center = c)
}

private fun DrawScope.drawPin(tip: Offset, color: Color) {
    val r = 11.dp.toPx()
    val cy = tip.y - r * 2.1f
    val pin = Path().apply {
        moveTo(tip.x, tip.y)
        cubicTo(tip.x - r * 0.6f, tip.y - r * 0.9f, tip.x - r, cy + r * 0.7f, tip.x - r, cy)
        arcTo(androidx.compose.ui.geometry.Rect(tip.x - r, cy - r, tip.x + r, cy + r), 180f, 180f, false)
        cubicTo(tip.x + r, cy + r * 0.7f, tip.x + r * 0.6f, tip.y - r * 0.9f, tip.x, tip.y)
        close()
    }
    drawPath(pin, color)
    drawCircle(Color.White, radius = r * 0.42f, center = Offset(tip.x, cy))
}
