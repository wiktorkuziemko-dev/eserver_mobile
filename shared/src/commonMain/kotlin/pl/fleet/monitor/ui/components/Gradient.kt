package pl.fleet.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.fleet.monitor.platform.StatusBarIcons
import pl.fleet.monitor.ui.theme.BrandGradient

/** Górny pasek z gradientem marki, tytułem i podtytułem. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradientTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    StatusBarIcons(white = true)
    Box(Modifier.background(BrandGradient)) {
        TopAppBar(
            title = {
                Column {
                    Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
        )
    }
}

/** Główny przycisk akcji z gradientem marki. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        shadowElevation = if (enabled) 3.dp else 0.dp,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) {
        Box(
            Modifier
                .clip(shape)
                .background(if (enabled) BrandGradient else androidx.compose.ui.graphics.SolidColor(Color(0xFFE0E0E0)))
                .padding(PaddingValues(horizontal = 20.dp, vertical = 12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides if (enabled) Color.White else Color(0xFF9E9E9E)) {
                Row(verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}
