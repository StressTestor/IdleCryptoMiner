package com.example.idleminer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()
    private val adManager: AdManager = AdManagerImpl()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IdleMinerTheme {
                GameScreen(viewModel, adManager, this)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveGame()
    }
}

val NeonGreen = Color(0xFF00FF00)
val DarkBackground = Color(0xFF050505)
val ErrorRed = Color(0xFFFF0000)

@Composable
fun IdleMinerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DarkBackground,
            surface = Color(0xFF101010),
            primary = NeonGreen,
            error = ErrorRed,
            onBackground = NeonGreen,
            onSurface = NeonGreen
        ),
        typography = Typography(
            bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
            titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
        ),
        content = content
    )
}

@Composable
fun GameScreen(viewModel: GameViewModel, adManager: AdManager, activity: ComponentActivity) {
    val hash by viewModel.hash.collectAsStateWithLifecycle()
    val upgrades by viewModel.upgrades.collectAsStateWithLifecycle()
    val boostEndTime by viewModel.boostEndTime.collectAsStateWithLifecycle()
    val offlineEarnings by viewModel.offlineEarnings.collectAsStateWithLifecycle()

    if (offlineEarnings > 0) {
        AlertDialog(
            onDismissRequest = { viewModel.clearOfflineEarnings() },
            title = { Text("OFFLINE EARNINGS") },
            text = { Text("You mined ${offlineEarnings.format()} Hash while offline.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearOfflineEarnings() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                ) {
                    Text("COLLECT")
                }
            },
            containerColor = Color(0xFF101010),
            titleContentColor = NeonGreen,
            textContentColor = Color.White
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "${hash.format()} HASH",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )
        
        val passiveRate = upgrades.sumOf { it.currentRate }
        Text(
            text = "+${passiveRate.format()}/sec",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Clicker (Fan)
        FanButton(onClick = { viewModel.onManualMine() })

        Spacer(modifier = Modifier.height(32.dp))

        // Boost Button
        val timeLeft = (boostEndTime - System.currentTimeMillis()) / 1000
        val isBoostActive = timeLeft > 0
        
        Button(
            onClick = {
                if (!isBoostActive) {
                    adManager.showInterstitial(activity) {
                        viewModel.activateBoost()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBoostActive) Color.DarkGray else NeonGreen,
                contentColor = if (isBoostActive) NeonGreen else Color.Black
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isBoostActive) {
                Text("OVERCLOCK ACTIVE: ${timeLeft}s")
            } else {
                Text("OVERCLOCK (WATCH AD)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Shop
        Text("HARDWARE SHOP", fontSize = 20.sp, color = NeonGreen, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(upgrades) { upgrade ->
                UpgradeItem(upgrade = upgrade, canAfford = hash >= upgrade.currentCost) {
                    viewModel.buyUpgrade(upgrade.id)
                }
            }
        }
    }
}

@Composable
fun FanButton(onClick: () -> Unit) {
    var isSpinning by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (isSpinning) 0.95f else 1f, label = "scale")

    LaunchedEffect(isSpinning) {
        if (isSpinning) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(durationMillis = 500, easing = LinearEasing)
            )
            isSpinning = false
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
                isSpinning = true
            }
    ) {
        // Draw Fan
        Canvas(modifier = Modifier.fillMaxSize().rotate(rotation.value)) {
            drawCircle(
                color = Color(0xFF202020),
                style = Stroke(width = 4.dp.toPx())
            )
            drawCircle(
                color = NeonGreen,
                radius = size.minDimension / 2 - 10f,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Blades
            val center = center
            val radius = size.minDimension / 2 - 20f
            for (i in 0 until 3) {
                val angle = i * 120f
                rotate(degrees = angle, pivot = center) {
                    drawPath(
                        path = Path().apply {
                            moveTo(center.x, center.y)
                            lineTo(center.x + 20f, center.y - radius)
                            lineTo(center.x - 20f, center.y - radius)
                            close()
                        },
                        color = Color(0xFF303030)
                    )
                }
            }
        }
        
        Text("GPU", color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun UpgradeItem(upgrade: Upgrade, canAfford: Boolean, onBuy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF101010), RoundedCornerShape(8.dp))
            .clickable(enabled = canAfford, onClick = onBuy)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(upgrade.name, color = Color.White, fontWeight = FontWeight.Bold)
            Text("+${upgrade.baseRate}/sec", color = Color.Gray, fontSize = 12.sp)
            Text("Owned: ${upgrade.count}", color = NeonGreen, fontSize = 12.sp)
        }
        
        Text(
            text = upgrade.currentCost.format(),
            color = if (canAfford) NeonGreen else ErrorRed,
            fontWeight = FontWeight.Bold
        )
    }
}

fun Double.format(): String {
    if (this >= 1_000_000_000) return String.format(Locale.US, "%.2fB", this / 1_000_000_000)
    if (this >= 1_000_000) return String.format(Locale.US, "%.2fM", this / 1_000_000)
    if (this >= 1_000) return String.format(Locale.US, "%.2fK", this / 1_000)
    return String.format(Locale.US, "%.0f", this)
}
