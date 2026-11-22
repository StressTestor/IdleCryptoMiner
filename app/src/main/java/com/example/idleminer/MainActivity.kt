package com.example.idleminer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IdleMinerTheme {
                GameScreen(viewModel)
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
val ForkCyan = Color(0xFF00BFFF)

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
fun GameScreen(viewModel: GameViewModel) {
    val hash by viewModel.hash.collectAsStateWithLifecycle()
    val upgrades by viewModel.upgrades.collectAsStateWithLifecycle()
    val boostEndTime by viewModel.boostEndTime.collectAsStateWithLifecycle()
    val offlineEarnings by viewModel.offlineEarnings.collectAsStateWithLifecycle()
    val prestigeCoins by viewModel.prestigeCoins.collectAsStateWithLifecycle()
    val runEarned by viewModel.runEarned.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val pendingCores = prestigeCoinsFor(runEarned)
    val multiplier = prestigeMultiplier(prestigeCoins)
    var showPrestigeDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val sound = remember { SoundManager(context) }
    DisposableEffect(Unit) { onDispose { sound.release() } }
    sound.muted = muted

    val haptic = LocalHapticFeedback.current
    // Drives the boost countdown so it ticks once a second instead of freezing.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize().background(DarkBackground),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = NeonGreen)
        }
        return
    }

    if (showSettings) {
        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull() ?: "1.0"
        }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("SETTINGS") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Sound", color = Color.White)
                        Switch(checked = !muted, onCheckedChange = { viewModel.setMuted(!it) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showSettings = false; showResetConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("RESET PROGRESS") }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Idle AI Bubble v$version", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        "Simulation game. No real AI was harmed. Not affiliated with any actual AI company. No backend, real money, or gambling.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettings = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                ) { Text("CLOSE") }
            },
            containerColor = Color(0xFF101010),
            titleContentColor = NeonGreen,
            textContentColor = Color.White,
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("RESET PROGRESS") },
            text = { Text("This wipes all tokens, models, and VC Billions. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetProgress(); showResetConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                ) { Text("RESET") }
            },
            dismissButton = {
                Button(
                    onClick = { showResetConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = NeonGreen),
                ) { Text("CANCEL") }
            },
            containerColor = Color(0xFF101010),
            titleContentColor = NeonGreen,
            textContentColor = Color.White,
        )
    }

    if (showPrestigeDialog) {
        AlertDialog(
            onDismissRequest = { showPrestigeDialog = false },
            title = { Text("FUNDING ROUND") },
            text = {
                Text(
                    "Reset tokens and models to bank $pendingCores VC Billions.\n\n" +
                        "VC Billions are permanent and each adds +10% to all production. " +
                        "Your multiplier becomes x" +
                        prestigeMultiplier(prestigeCoins + pendingCores)
                            .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.prestige(); sound.fork(); showPrestigeDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                ) { Text("PIVOT TO AGI") }
            },
            dismissButton = {
                Button(
                    onClick = { showPrestigeDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = NeonGreen)
                ) { Text("CANCEL") }
            },
            containerColor = Color(0xFF101010),
            titleContentColor = NeonGreen,
            textContentColor = Color.White
        )
    }

    if (offlineEarnings.signum() > 0) {
        AlertDialog(
            onDismissRequest = { viewModel.clearOfflineEarnings() },
            title = { Text("OFFLINE EARNINGS") },
            text = { Text("You generated ${formatBig(offlineEarnings)} Tokens while offline.") },
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
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "${formatBig(hash)} TOKENS",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )

        val rate = passiveRate(upgrades)
        Text(
            text = "+${formatBig(rate)} Tokens/sec",
            fontSize = 16.sp,
            color = Color.Gray
        )

        if (prestigeCoins > 0) {
            Text(
                text = "VC x${multiplier.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()} ($prestigeCoins VC Billions)",
                fontSize = 14.sp,
                color = ForkCyan
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Clicker (Fan)
        FanButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            sound.tap()
            viewModel.onManualMine()
        })

        Spacer(modifier = Modifier.height(32.dp))

        // Hype Cycle: free, cooldown-gated boost (no ad). State derived from now.
        val boostActiveLeft = (boostEndTime - now) / 1000
        val cooldownLeft = (boostEndTime + BOOST_COOLDOWN_MS - now) / 1000
        val isBoostActive = boostActiveLeft > 0
        val onCooldown = !isBoostActive && cooldownLeft > 0

        Button(
            onClick = {
                if (!isBoostActive && !onCooldown) viewModel.activateBoost()
            },
            enabled = !isBoostActive && !onCooldown,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonGreen,
                contentColor = Color.Black,
                disabledContainerColor = Color.DarkGray,
                disabledContentColor = NeonGreen
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                when {
                    isBoostActive -> "HYPE CYCLE ACTIVE: ${boostActiveLeft}s"
                    onCooldown -> "COOLDOWN: ${cooldownLeft}s"
                    else -> "HYPE CYCLE (2x FOR 5 MIN)"
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Prestige (Funding Round)
        val canFork = pendingCores >= 1
        Button(
            onClick = { if (canFork) showPrestigeDialog = true },
            enabled = canFork,
            colors = ButtonDefaults.buttonColors(
                containerColor = ForkCyan,
                contentColor = Color.Black,
                disabledContainerColor = Color(0xFF202020),
                disabledContentColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(if (canFork) "FUNDING ROUND (+$pendingCores)" else "FUNDING ROUND (LOCKED)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Shop
        Text("MODEL SHOP", fontSize = 20.sp, color = NeonGreen, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            upgrades.forEach { upgrade ->
                UpgradeItem(upgrade = upgrade, canAfford = hash >= upgrade.currentCost) {
                    sound.buy()
                    viewModel.buyUpgrade(upgrade.id)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { showSettings = true }) {
            Text("SETTINGS", color = Color.Gray)
        }
    }
}

@Composable
fun FanButton(onClick: () -> Unit) {
    var isSpinning by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (isSpinning) 0.95f else 1f, label = "scale")

    LaunchedEffect(isSpinning) {
        if (isSpinning) {
            delay(500)
            isSpinning = false
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .scale(scale)
            .semantics {
                contentDescription = "Prompt"
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "tap to prompt",
            ) {
                onClick()
                isSpinning = true
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = NeonGreen,
                topLeft = androidx.compose.ui.geometry.Offset(12.dp.toPx(), 30.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width - 24.dp.toPx(), size.height - 60.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
            drawLine(
                color = NeonGreen,
                start = androidx.compose.ui.geometry.Offset(45.dp.toPx(), 170.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(30.dp.toPx(), 185.dp.toPx()),
                strokeWidth = 3.dp.toPx()
            )
        }
        
        Text("Prompt", color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun UpgradeItem(upgrade: Upgrade, canAfford: Boolean, onBuy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (canAfford) 1f else 0.45f) // non-color affordability cue
            .background(Color(0xFF101010), RoundedCornerShape(8.dp))
            .clickable(enabled = canAfford, onClick = onBuy)
            .semantics {
                contentDescription = "${upgrade.name}, " +
                    (if (canAfford) "affordable" else "locked") +
                    ", cost ${formatBig(upgrade.currentCost)}, owned ${upgrade.count}"
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(upgrade.name, color = Color.White, fontWeight = FontWeight.Bold)
            Text("+${formatBig(upgrade.baseRate)} Tokens/sec", color = Color.Gray, fontSize = 12.sp)
            Text("Owned: ${upgrade.count}", color = NeonGreen, fontSize = 12.sp)
        }

        Text(
            text = formatBig(upgrade.currentCost),
            color = if (canAfford) NeonGreen else ErrorRed,
            fontWeight = FontWeight.Bold
        )
    }
}
