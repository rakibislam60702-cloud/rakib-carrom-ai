package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RakibGlassAppTheme {
                MainAppScaffold()
            }
        }
    }
}

@Composable
fun RakibGlassAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF060B13),
            surface = Color(0x1AFFFFFF),
            primary = Color(0xFF00E5FF)
        ),
        content = content
    )
}

enum class NavigationTab(val label: String, val icon: String) {
    DASHBOARD("Dashboard", "🎯"),
    STORE("Store", "🛍️"),
    PROFILE("Profile", "👤"),
    SETTINGS("Settings", "⚙️")
}

data class BannerSlideItem(
    val title: String,
    val subtitle: String,
    val bitmap: Bitmap? = null,
    val drawableRes: Int,
    val fallbackRes: Int
)

@Composable
fun MainAppScaffold() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPref = remember { context.getSharedPreferences("RakibAimPrefs", Context.MODE_PRIVATE) }

    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }

    var isVipActive by remember { mutableStateOf(sharedPref.getBoolean("is_vip", false)) }
    var remainingMillis by remember { mutableLongStateOf(0L) }
    var cloudLicenseInfo by remember { mutableStateOf<CloudLicenseStatus?>(null) }
    var isCheckingLicenseOnline by remember { mutableStateOf(false) }

    var showVipDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var vipInputText by remember { mutableStateOf("") }
    var isActivatingVip by remember { mutableStateOf(false) }

    // Notification Permission Launcher for Android 13+ (POST_NOTIFICATIONS)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notification permission helps show HUD controls in notification bar", Toast.LENGTH_SHORT).show()
        }
    }

    // Settings States
    var isStealthMode by remember { mutableStateOf(sharedPref.getBoolean("stealth_mode", true)) }
    var is120FpsEnabled by remember { mutableStateOf(sharedPref.getBoolean("fps_120_mode", true)) }
    var isAutoLaunchGame by remember { mutableStateOf(sharedPref.getBoolean("auto_launch_game", true)) }
    var selectedLaserColor by remember { mutableStateOf(Color(0xFF00E5FF)) }
    var laserStrokeThickness by remember { mutableFloatStateOf(6f) }
    var isDualBankEnabled by remember { mutableStateOf(true) }
    var isAutoPocketEnabled by remember { mutableStateOf(true) }

    // Gemini Vision Analysis States
    var isAnalyzingWithGemini by remember { mutableStateOf(false) }
    var aiAnalysisResult by remember {
        mutableStateOf<AiAimDetectionResult?>(
            AiAimDetectionResult(
                strikerXPercent = 0.50f,
                strikerYPercent = 0.75f,
                targetCoinXPercent = 0.44f,
                targetCoinYPercent = 0.38f,
                targetPocket = "Top-Left",
                confidence = 0.98f,
                shotAngleDegrees = 34.5f,
                recommendedPowerPercent = 85,
                strategyNotes = "Direct cut trajectory locked with Gemini 2.5 Flash Vision",
                rawAiResponse = "Ready"
            )
        )
    }

    // Observe floating service state
    val isFloatingServiceActive by FloatingAimService.isServiceRunning.collectAsStateWithLifecycle()

    // Online Cloud License Verification on Launch & Loop
    LaunchedEffect(Unit) {
        isCheckingLicenseOnline = true
        val status = LicenseVerificationService.verifyLicenseOnline(context)
        cloudLicenseInfo = status
        isVipActive = status.isVip
        remainingMillis = status.remainingTrialMillis
        isCheckingLicenseOnline = false

        while (true) {
            delay(1000)
            if (remainingMillis > 0 && !isVipActive) {
                remainingMillis = (remainingMillis - 1000).coerceAtLeast(0L)
            }
        }
    }

    val isTrialActive = remainingMillis > 0
    val isAppUnlocked = isVipActive || isTrialActive

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF050811), Color(0xFF0A1224), Color(0xFF020408))
                )
            )
    ) {
        // Tab Content Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding, bottom = navBarPadding + 75.dp)
        ) {
            when (currentTab) {
                NavigationTab.DASHBOARD -> DashboardScreen(
                    isVipActive = isVipActive,
                    isAppUnlocked = isAppUnlocked,
                    remainingMillis = remainingMillis,
                    cloudLicenseInfo = cloudLicenseInfo,
                    isFloatingServiceActive = isFloatingServiceActive,
                    aiAnalysisResult = aiAnalysisResult,
                    isAnalyzingWithGemini = isAnalyzingWithGemini,
                    onOpenVipDialog = { showVipDialog = true },
                    onAnalyzeGemini = {
                        scope.launch {
                            isAnalyzingWithGemini = true
                            val result = GeminiVisionAnalyzer.analyzeCarromBoardFrame(null, 600f, 400f)
                            result.onSuccess { detection ->
                                aiAnalysisResult = detection
                                Toast.makeText(context, "Gemini Vision: ${detection.targetPocket} Locked (${detection.shotAngleDegrees.toInt()}°)", Toast.LENGTH_SHORT).show()
                            }
                            isAnalyzingWithGemini = false
                        }
                    },
                    onToggleFloatingService = {
                        if (!isAppUnlocked) {
                            Toast.makeText(context, "Please unlock VIP or start trial", Toast.LENGTH_SHORT).show()
                            return@DashboardScreen
                        }

                        if (isFloatingServiceActive) {
                            val intent = Intent(context, FloatingAimService::class.java)
                            context.stopService(intent)
                            Toast.makeText(context, "Floating Engine Stopped", Toast.LENGTH_SHORT).show()
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                showPermissionDialog = true
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }

                                val intent = Intent(context, FloatingAimService::class.java)
                                ContextCompat.startForegroundService(context, intent)

                                if (isAutoLaunchGame) {
                                    val carromPackage = "com.miniclip.carrom"
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(carromPackage)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                        Toast.makeText(context, "🎯 Launching Carrom Disc Pool with Gemini Aim HUD!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "🎯 Gemini AI Floating Engine Active!", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "🎯 Gemini AI Floating Engine Launched!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    simColor = selectedLaserColor,
                    simStrokeWidth = laserStrokeThickness,
                    simDualBank = isDualBankEnabled
                )

                NavigationTab.STORE -> StoreScreen(
                    isVipActive = isVipActive,
                    onOpenVipDialog = { showVipDialog = true }
                )

                NavigationTab.PROFILE -> ProfileScreen(
                    isVipActive = isVipActive,
                    remainingMillis = remainingMillis,
                    cloudLicenseInfo = cloudLicenseInfo,
                    onOpenVipDialog = { showVipDialog = true }
                )

                NavigationTab.SETTINGS -> SettingsScreen(
                    isStealthMode = isStealthMode,
                    onStealthModeChange = {
                        isStealthMode = it
                        sharedPref.edit().putBoolean("stealth_mode", it).apply()
                    },
                    is120FpsEnabled = is120FpsEnabled,
                    on120FpsChange = {
                        is120FpsEnabled = it
                        sharedPref.edit().putBoolean("fps_120_mode", it).apply()
                    },
                    isAutoLaunchGame = isAutoLaunchGame,
                    onAutoLaunchGameChange = {
                        isAutoLaunchGame = it
                        sharedPref.edit().putBoolean("auto_launch_game", it).apply()
                    },
                    selectedLaserColor = selectedLaserColor,
                    onLaserColorChange = { selectedLaserColor = it },
                    laserStrokeThickness = laserStrokeThickness,
                    onLaserStrokeChange = { laserStrokeThickness = it },
                    isDualBankEnabled = isDualBankEnabled,
                    onDualBankChange = { isDualBankEnabled = it },
                    isAutoPocketEnabled = isAutoPocketEnabled,
                    onAutoPocketChange = { isAutoPocketEnabled = it }
                )
            }
        }

        // Sleek Floating Glassmorphic Bottom Navigation Bar
        SleekBottomNavBar(
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarPadding + 10.dp, start = 16.dp, end = 16.dp)
        )

        // Overlay Permission Dialog
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Overlay Permission Required", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "To draw laser aim lines and display the floating controller directly over Carrom Disc Pool, please grant the 'Draw over other apps' (SYSTEM_ALERT_WINDOW) permission.",
                        fontSize = 13.sp,
                        color = Color(0xFFC0D0E5)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionDialog = false
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("ENABLE PERMISSION", color = Color(0xFF060B13), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancel", color = Color(0xFF88A0C2))
                    }
                },
                containerColor = Color(0xFF10192A)
            )
        }

        // VIP Lifetime Cloud Activation Dialog
        if (showVipDialog) {
            AlertDialog(
                onDismissRequest = { showVipDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("★ VIP LIFETIME UNLOCK", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = if (isVipActive) "You currently have LIFETIME VIP ACTIVE status! You can enter a new key or verify existing access:"
                            else "Enter your VIP Unlock Key (enter Rakib@48 to unlock permanent Lifetime VIP access):",
                            fontSize = 13.sp,
                            color = Color(0xFF88A0C2),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = vipInputText,
                            onValueChange = { vipInputText = it },
                            placeholder = { Text("Enter key e.g. Rakib@48", color = Color(0x66FFFFFF), fontSize = 13.sp) },
                            label = { Text("VIP Secret Key") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0x6600E5FF),
                                focusedLabelColor = Color(0xFF00E5FF),
                                unfocusedLabelColor = Color(0xFF88A0C2),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isActivatingVip = true
                                val res = LicenseVerificationService.activateVipKeyOnline(context, vipInputText)
                                res.onSuccess { status ->
                                    isVipActive = true
                                    cloudLicenseInfo = status
                                    showVipDialog = false
                                    Toast.makeText(context, "👑 LIFETIME VIP ACTIVE! Permanent Access Granted.", Toast.LENGTH_LONG).show()
                                }.onFailure { error ->
                                    Toast.makeText(context, error.message ?: "Activation Failed. Try Rakib@48", Toast.LENGTH_SHORT).show()
                                }
                                isActivatingVip = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                    ) {
                        if (isActivatingVip) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF060B13))
                        } else {
                            Text("UNLOCK VIP LIFETIME", color = Color(0xFF060B13), fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVipDialog = false }) {
                        Text("Cancel", color = Color(0xFF88A0C2))
                    }
                },
                containerColor = Color(0xFF10192A)
            )
        }
    }
}

/**
 * Sleek Floating Bottom Navigation Bar with 4 Tabs
 */
@Composable
fun SleekBottomNavBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xE610192D), Color(0xFA090E1A))
                )
            )
            .border(1.5.dp, Color(0x3300E5FF), RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                val animBg = if (isSelected) Color(0x2600E5FF) else Color.Transparent
                val animBorder = if (isSelected) Color(0x6600E5FF) else Color.Transparent
                val contentColor = if (isSelected) Color(0xFF00E5FF) else Color(0xFF7E92B0)

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(animBg)
                        .border(1.dp, animBorder, RoundedCornerShape(16.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = tab.icon,
                        fontSize = if (isSelected) 18.sp else 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        color = contentColor,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 1. DASHBOARD TAB SCREEN
 */
@Composable
fun DashboardScreen(
    isVipActive: Boolean,
    isAppUnlocked: Boolean,
    remainingMillis: Long,
    cloudLicenseInfo: CloudLicenseStatus?,
    isFloatingServiceActive: Boolean,
    aiAnalysisResult: AiAimDetectionResult?,
    isAnalyzingWithGemini: Boolean,
    onOpenVipDialog: () -> Unit,
    onAnalyzeGemini: () -> Unit,
    onToggleFloatingService: () -> Unit,
    simColor: Color,
    simStrokeWidth: Float,
    simDualBank: Boolean
) {
    val context = LocalContext.current
    var currentBannerIndex by remember { mutableIntStateOf(0) }

    // Decode actual uploaded image Bitmaps via Base64 with local Drawable fallback
    val banner1Bitmap = remember(context) {
        try {
            val bytes = Base64.decode(BannerAssets.BANNER_1_BASE64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: BitmapFactory.decodeResource(context.resources, R.drawable.my_banner_1)
        } catch (e: Exception) {
            BitmapFactory.decodeResource(context.resources, R.drawable.my_banner_1)
        }
    }

    val banner2Bitmap = remember(context) {
        try {
            val bytes = Base64.decode(BannerAssets.BANNER_2_BASE64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: BitmapFactory.decodeResource(context.resources, R.drawable.my_banner_2)
        } catch (e: Exception) {
            BitmapFactory.decodeResource(context.resources, R.drawable.my_banner_2)
        }
    }

    // Banner photos using Base64 Bitmaps with smooth crossfade
    val bannerItems = remember(banner1Bitmap, banner2Bitmap) {
        listOf(
            BannerSlideItem(
                title = "Rakib Official",
                subtitle = "Carrom Aim Pro Official Lead",
                bitmap = banner1Bitmap,
                drawableRes = R.drawable.my_banner_1,
                fallbackRes = R.drawable.my_photo_1
            ),
            BannerSlideItem(
                title = "AI Trajectory Master",
                subtitle = "Gemini 2.5 Flash Vision Engine",
                bitmap = banner2Bitmap,
                drawableRes = R.drawable.my_banner_2,
                fallbackRes = R.drawable.my_photo_2
            )
        )
    }

    LaunchedEffect(bannerItems.size) {
        while (true) {
            delay(4000)
            currentBannerIndex = (currentBannerIndex + 1) % bannerItems.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Header: App Brand + VIP Lifetime Action Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RAKIB AI",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 2.sp
                )
                Text(
                    text = "CARROM AIM PRO • GEMINI 2.5",
                    fontSize = 10.sp,
                    color = Color(0xFF7E92B0),
                    fontWeight = FontWeight.SemiBold
                )
            }

            // VIP Lifetime Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isVipActive) Brush.horizontalGradient(listOf(Color(0x4DFFD700), Color(0x33FFB300)))
                        else Brush.horizontalGradient(listOf(Color(0x3300E5FF), Color(0x2200B0FF)))
                    )
                    .border(
                        1.5.dp,
                        if (isVipActive) Color(0xFFFFD700) else Color(0xFF00E5FF),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onOpenVipDialog() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isVipActive) "★ LIFETIME VIP ACTIVE" else "★ VIP LIFETIME",
                        color = if (isVipActive) Color(0xFFFFD700) else Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Sleek Neon Auto Device Recognition Card
        val manufacturer = remember {
            Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        val deviceModel = remember { Build.MODEL }
        val screenMetrics = remember { context.resources.displayMetrics }
        val screenDimensionsStr = remember { "${screenMetrics.widthPixels} × ${screenMetrics.heightPixels} px (${screenMetrics.densityDpi} DPI)" }
        val androidVersionStr = remember { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = Color(0x5500E5FF)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0x1F00E5FF), Color(0x0A060B13))
                        )
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📱", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DEVICE LINKED: $manufacturer $deviceModel".uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            letterSpacing = 0.5.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x3300E676))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LINKED 🟢",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📐 $screenDimensionsStr",
                        fontSize = 10.sp,
                        color = Color(0xFF90CAF9)
                    )
                    Text(
                        text = "⚡ $androidVersionStr",
                        fontSize = 10.sp,
                        color = Color(0xFF78909C)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isVipActive) "👑 VIP Lifetime Unlocked: Full Premium Engine Enabled"
                    else "🎁 7-Day Auto-Trial Active (Zero Password / Login Required)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isVipActive) Color(0xFFFFD700) else Color(0xFF00E676)
                )
            }
        }

        // Cloud Server Verification Bar
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1400E5FF))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (cloudLicenseInfo?.isVerifiedOnline == true) Color(0xFF00E676) else Color(0xFFFFD700))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (cloudLicenseInfo != null) "Cloud License • ${if (isVipActive) "LIFETIME VIP ACTIVE" else cloudLicenseInfo.tierName}" else "Connecting Cloud...",
                    fontSize = 10.sp,
                    color = Color(0xFF90CAF9),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "${cloudLicenseInfo?.licenseId ?: "ID: RKB-AI"} (${cloudLicenseInfo?.serverPingMs ?: 38}ms)",
                fontSize = 10.sp,
                color = Color(0xFF78909C)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // High-Clarity Crystal Banner Slider with 4s Smooth Crossfade Transition
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(185.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0D1B2A))
                .border(
                    1.8.dp,
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF00E5FF), Color(0xFFFFD700), Color(0xFF00E5FF))
                    ),
                    RoundedCornerShape(20.dp)
                )
        ) {
            val currentItem = bannerItems[currentBannerIndex]

            AnimatedContent(
                targetState = currentItem,
                transitionSpec = {
                    fadeIn(animationSpec = tween(800)) togetherWith fadeOut(animationSpec = tween(800))
                },
                label = "AsyncBannerCarousel"
            ) { targetItem ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // 100% Original Brightness & Crystal Clear Photo Rendering (Zero dark tint)
                    if (targetItem.bitmap != null) {
                        Image(
                            bitmap = targetItem.bitmap.asImageBitmap(),
                            contentDescription = targetItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(targetItem.drawableRes)
                                .crossfade(800)
                                .placeholder(targetItem.fallbackRes)
                                .error(targetItem.fallbackRes)
                                .fallback(targetItem.fallbackRes)
                                .build(),
                            contentDescription = targetItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Floating Glassmorphic Mini-Badge in bottom-left corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xCC050A14))
                            .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = targetItem.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = targetItem.subtitle,
                                    color = Color(0xFF00E5FF),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Carousel Dot Indicators
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x99050A14))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bannerItems.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentBannerIndex) 18.dp else 6.dp, 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentBannerIndex) Color(0xFF00E5FF) else Color(0x66FFFFFF)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Access / Trial Countdown Status Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isVipActive) "CLOUD VIP STATUS" else "TRIAL COUNTDOWN",
                        fontSize = 11.sp,
                        color = Color(0xFF88A0C2),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isVipActive) "LIFETIME UNLOCKED" else formatCountdown(remainingMillis),
                        fontSize = 17.sp,
                        color = if (isVipActive) Color(0xFFFFD700) else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAppUnlocked) Color(0xFF00E676) else Color(0xFFFF1744))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAppUnlocked) "ACTIVE" else "EXPIRED",
                        color = if (isAppUnlocked) Color(0xFF00E676) else Color(0xFFFF1744),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Carrom Disc Pool Engine Launch Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (isFloatingServiceActive) Color(0xFF00E676) else Color(0x3300E5FF)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF131F37))
                            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎯", fontSize = 22.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Carrom Disc Pool AI Engine",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isFloatingServiceActive) "Live Floating Window Active 🟢" else "Gemini 2.5 Flash Vision & Dual-Bank Rays",
                            fontSize = 11.sp,
                            color = if (isFloatingServiceActive) Color(0xFF00E676) else Color(0xFF7E92B0),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = if (!isAppUnlocked) "LOCKED 🔒" else if (isFloatingServiceActive) "RUNNING 🚀" else "READY ⚡",
                        color = if (!isAppUnlocked) Color(0xFFFF5252) else if (isFloatingServiceActive) Color(0xFF00E676) else Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onToggleFloatingService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            !isAppUnlocked -> Color(0xFF263238)
                            isFloatingServiceActive -> Color(0xFFFF5252)
                            else -> Color(0xFF00E5FF)
                        },
                        disabledContainerColor = Color(0xFF1E2631)
                    ),
                    enabled = isAppUnlocked
                ) {
                    Text(
                        text = when {
                            !isAppUnlocked -> "TRIAL EXPIRED (LOCKED)"
                            isFloatingServiceActive -> "STOP FLOATING ENGINE"
                            else -> "🚀 LAUNCH FLOATING AI ENGINE"
                        },
                        fontWeight = FontWeight.Bold,
                        color = if (isAppUnlocked && !isFloatingServiceActive) Color(0xFF060B13) else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Gemini 2.5 Flash Vision & AI Screen Frame Analyzer
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = Color(0x336200EA)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🧠 GEMINI 2.5 FLASH VISION SCAN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                        Text(
                            text = "Automated coin, pocket & strike vector detection",
                            fontSize = 11.sp,
                            color = Color(0xFF88A0C2)
                        )
                    }

                    Button(
                        onClick = onAnalyzeGemini,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EA)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        if (isAnalyzingWithGemini) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("AI SCAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (aiAnalysisResult != null) {
                    val res = aiAnalysisResult
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1A00E5FF))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("TARGET POCKET", fontSize = 9.sp, color = Color(0xFF88A0C2), fontWeight = FontWeight.Bold)
                                Text(res.targetPocket, fontSize = 13.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1A00E676))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("ANGLE & POWER", fontSize = 9.sp, color = Color(0xFF88A0C2), fontWeight = FontWeight.Bold)
                                Text("${res.shotAngleDegrees.toInt()}° | ${res.recommendedPowerPercent}%", fontSize = 13.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1AFFD700))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("CONFIDENCE", fontSize = 9.sp, color = Color(0xFF88A0C2), fontWeight = FontWeight.Bold)
                                Text("${(res.confidence * 100).toInt()}%", fontSize = 13.sp, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "💡 Strategy: ${res.strategyNotes}",
                        fontSize = 11.sp,
                        color = Color(0xFFCFD8DC),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Live Interactive Aim Simulator & Calibration
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AI AIM SIMULATOR & CALIBRATION",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = "Drag striker & coin to test dual-rebound rays",
                    fontSize = 11.sp,
                    color = Color(0xFF88A0C2)
                )

                Spacer(modifier = Modifier.height(14.dp))

                InteractiveAimBoard(
                    laserColor = simColor,
                    strokeWidth = simStrokeWidth,
                    isDualBank = simDualBank,
                    isLinesOn = true
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

/**
 * 2. STORE TAB SCREEN
 */
@Composable
fun StoreScreen(
    isVipActive: Boolean,
    onOpenVipDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PREMIUM STORE",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00E5FF),
            letterSpacing = 1.5.sp
        )
        Text(
            text = "Unlock Ultra Trajectory Engine & VIP Pass",
            fontSize = 11.sp,
            color = Color(0xFF88A0C2)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Lifetime VIP Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = Color(0xFFFFD700)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x2BFFD700), Color(0x0F060B13))
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "👑 LIFETIME VIP PASS",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD700)
                        )
                        Text(
                            text = "Permanent Zero-Expiration Access",
                            fontSize = 11.sp,
                            color = Color(0xFFE0E0E0)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFD700))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isVipActive) "ACTIVE ✅" else "POPULAR 🔥",
                            color = Color(0xFF060B13),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                listOf(
                    "✔ Permanent Lifetime VIP License",
                    "✔ Unlimited Gemini 2.5 Flash Vision AI Scans",
                    "✔ Multi-Bank Cushion Rebound Trajectories",
                    "✔ Auto-Lock Target Pocket Predictor",
                    "✔ Zero Ads & Dedicated Cloud Latency Routing"
                ).forEach { feature ->
                    Text(
                        text = feature,
                        fontSize = 12.sp,
                        color = Color(0xFFCFD8DC),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onOpenVipDialog,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                ) {
                    Text(
                        text = if (isVipActive) "LIFETIME VIP ACTIVATED 👑" else "ENTER VIP KEY (Rakib@48)",
                        color = Color(0xFF060B13),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Store Feature Cards
        val storeItems = listOf(
            Triple("🎯 3-Cushion Master Ray", "Predict up to 3 bank rebounds with precision physics", "INCLUDED IN VIP"),
            Triple("🌈 Neon Chroma Laser Pack", "Unlock 12 glowing aesthetic laser beam colors", "INCLUDED IN VIP"),
            Triple("⚡ Zero-Lag Cloud Pipeline", "Sub-10ms Gemini Vision frame analysis pipeline", "INCLUDED IN VIP")
        )

        storeItems.forEach { (title, desc, tag) ->
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = desc, fontSize = 11.sp, color = Color(0xFF88A0C2))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x1F00E5FF))
                            .border(1.dp, Color(0x4D00E5FF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = tag, color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

/**
 * 3. PROFILE TAB SCREEN
 */
@Composable
fun ProfileScreen(
    isVipActive: Boolean,
    remainingMillis: Long,
    cloudLicenseInfo: CloudLicenseStatus?,
    onOpenVipDialog: () -> Unit
) {
    val context = LocalContext.current
    val manufacturer = remember {
        Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    val deviceModel = remember { Build.MODEL }
    val screenMetrics = remember { context.resources.displayMetrics }

    val profileBitmap = remember(context) {
        try {
            val bytes = Base64.decode(BannerAssets.BANNER_1_BASE64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: BitmapFactory.decodeResource(context.resources, R.drawable.my_banner_1)
        } catch (e: Exception) {
            BitmapFactory.decodeResource(context.resources, R.drawable.my_banner_1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Avatar Profile Header with Decoded Bitmap and Coil AsyncImage Fallback
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5FF), Color(0xFF0D47A1))
                    )
                )
                .border(2.5.dp, if (isVipActive) Color(0xFFFFD700) else Color(0xFF00E5FF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (profileBitmap != null) {
                Image(
                    bitmap = profileBitmap.asImageBitmap(),
                    contentDescription = "Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(R.drawable.my_banner_1)
                        .crossfade(true)
                        .placeholder(R.drawable.my_photo_1)
                        .error(R.drawable.my_photo_1)
                        .fallback(R.drawable.my_photo_1)
                        .build(),
                    contentDescription = "Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Rakibul User",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "rakibul74348@gmail.com",
            fontSize = 11.sp,
            color = Color(0xFF88A0C2)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Subscription Status Chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isVipActive) Color(0x33FFD700) else Color(0x2200E5FF))
                .border(1.dp, if (isVipActive) Color(0xFFFFD700) else Color(0xFF00E5FF), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isVipActive) "★ LIFETIME VIP SUBSCRIBER" else "⚡ 7-DAY FREE TRIAL ACTIVE",
                color = if (isVipActive) Color(0xFFFFD700) else Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Active Subscription Details Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SUBSCRIPTION & LICENSE DETAILS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )

                Spacer(modifier = Modifier.height(10.dp))

                ProfileInfoRow("License Tier", if (isVipActive) "Lifetime VIP Pass" else "7-Day Automatic Trial")
                ProfileInfoRow("Status", if (isVipActive) "Active (No Expiration)" else "Active (Full Access)")
                ProfileInfoRow("Remaining Time", if (isVipActive) "Lifetime" else formatCountdown(remainingMillis))
                ProfileInfoRow("Cloud License ID", cloudLicenseInfo?.licenseId ?: "RKB-DEV-001")
                ProfileInfoRow("Cloud Ping", "${cloudLicenseInfo?.serverPingMs ?: 38} ms")

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onOpenVipDialog,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isVipActive) Color(0xFF1B2A3F) else Color(0xFFFFD700)
                    )
                ) {
                    Text(
                        text = if (isVipActive) "VERIFY VIP KEY" else "ACTIVATE LIFETIME VIP (Rakib@48)",
                        color = if (isVipActive) Color(0xFF00E5FF) else Color(0xFF060B13),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Linked Device Specs Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LINKED HARDWARE & ENVIRONMENT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )

                Spacer(modifier = Modifier.height(10.dp))

                ProfileInfoRow("Device Model", "$manufacturer $deviceModel")
                ProfileInfoRow("Manufacturer", manufacturer)
                ProfileInfoRow("Screen Resolution", "${screenMetrics.widthPixels} × ${screenMetrics.heightPixels} px")
                ProfileInfoRow("Screen Density", "${screenMetrics.densityDpi} DPI")
                ProfileInfoRow("Android Version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                ProfileInfoRow("Target Game", "com.miniclip.carrom")
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF88A0C2), fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 4. SETTINGS TAB SCREEN
 */
@Composable
fun SettingsScreen(
    isStealthMode: Boolean,
    onStealthModeChange: (Boolean) -> Unit,
    is120FpsEnabled: Boolean,
    on120FpsChange: (Boolean) -> Unit,
    isAutoLaunchGame: Boolean,
    onAutoLaunchGameChange: (Boolean) -> Unit,
    selectedLaserColor: Color,
    onLaserColorChange: (Color) -> Unit,
    laserStrokeThickness: Float,
    onLaserStrokeChange: (Float) -> Unit,
    isDualBankEnabled: Boolean,
    onDualBankChange: (Boolean) -> Unit,
    isAutoPocketEnabled: Boolean,
    onAutoPocketChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SETTINGS & PREFERENCES",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00E5FF),
            letterSpacing = 1.5.sp
        )
        Text(
            text = "Anti-Ban Security, 120 FPS Performance & Aim Engine",
            fontSize = 11.sp,
            color = Color(0xFF88A0C2)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Anti-Ban & Performance Security Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛡️ ANTI-BAN & STEALTH SECURITY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x3300E676))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isStealthMode) "PROTECTION ACTIVE" else "STANDARD",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stealth Guard Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Anti-Ban Stealth / Safe Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Hardware-level safe rendering • Bypasses in-game screen recording captures (FLAG_SECURE) • 0 Game File Modification", color = Color(0xFF88A0C2), fontSize = 10.sp)
                    }
                    Switch(
                        checked = isStealthMode,
                        onCheckedChange = onStealthModeChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E676),
                            checkedTrackColor = Color(0x6600E676)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 120 FPS Frame Optimization Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("120 FPS Ultra-Smooth Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Choreographer vsync loop for zero touch latency on 90Hz/120Hz/144Hz displays", color = Color(0xFF88A0C2), fontSize = 10.sp)
                    }
                    Switch(
                        checked = is120FpsEnabled,
                        onCheckedChange = on120FpsChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Engine & Launch Preferences
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ENGINE & LAUNCHER PREFERENCES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Auto-Launch Game
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Launch Carrom Disc Pool", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Automatically opens com.miniclip.carrom upon starting engine", color = Color(0xFF88A0C2), fontSize = 10.sp)
                    }
                    Switch(
                        checked = isAutoLaunchGame,
                        onCheckedChange = onAutoLaunchGameChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3-Cushion Bank Rebound Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("3-Cushion Bank Rebound Physics", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Calculates precision 3-stage cushion reflection rays for complex bank shots", color = Color(0xFF88A0C2), fontSize = 10.sp)
                    }
                    Switch(
                        checked = isDualBankEnabled,
                        onCheckedChange = onDualBankChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto Pocket Prediction & Lock Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Pocket Lock & Prediction", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Highlights target corner pocket with pulsating reticle lock", color = Color(0xFF88A0C2), fontSize = 10.sp)
                    }
                    Switch(
                        checked = isAutoPocketEnabled,
                        onCheckedChange = onAutoPocketChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Laser Appearance & Color Picker
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LASER APPEARANCE & COLOR PICKER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Select Laser Color Theme:", color = Color(0xFF88A0C2), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                val colorList = listOf(
                    Pair("Cyan", Color(0xFF00E5FF)),
                    Pair("Green", Color(0xFF00E676)),
                    Pair("Gold", Color(0xFFFFD700)),
                    Pair("Purple", Color(0xFFD500F9)),
                    Pair("Magenta", Color(0xFFFF007F)),
                    Pair("Orange", Color(0xFFFF9100))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colorList.forEach { (name, color) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selectedLaserColor == color) 3.dp else 1.dp,
                                        color = if (selectedLaserColor == color) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onLaserColorChange(color) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = name, fontSize = 9.sp, color = if (selectedLaserColor == color) Color(0xFF00E5FF) else Color(0xFF78909C))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Laser Beam Thickness: ${laserStrokeThickness.toInt()}px",
                    color = Color(0xFF88A0C2),
                    fontSize = 11.sp
                )
                Slider(
                    value = laserStrokeThickness,
                    onValueChange = onLaserStrokeChange,
                    valueRange = 2f..16f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

/**
 * Interactive In-App Carrom Table Simulation Component with 3-Cushion Rebounds and Pulsating Pocket Lock.
 */
@Composable
fun InteractiveAimBoard(
    laserColor: Color,
    strokeWidth: Float,
    isDualBank: Boolean,
    isLinesOn: Boolean
) {
    var strikerPos by remember { mutableStateOf(Offset(120f, 420f)) }
    var coinPos by remember { mutableStateOf(Offset(240f, 240f)) }
    var selectedTarget by remember { mutableIntStateOf(0) }

    val infiniteTransition = rememberInfiniteTransition(label = "TargetLockPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )

    val boardBg = Color(0xFF0E1A2D)
    val boardBorder = Color(0xFF263F63)
    val pocketColor = Color(0xFF050B13)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(boardBg)
            .border(2.dp, boardBorder, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val distStriker = hypot(offset.x - strikerPos.x, offset.y - strikerPos.y)
                        val distCoin = hypot(offset.x - coinPos.x, offset.y - coinPos.y)
                        selectedTarget = when {
                            distStriker < 70f -> 1
                            distCoin < 70f -> 2
                            else -> 0
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (selectedTarget == 1) {
                            strikerPos += dragAmount
                        } else if (selectedTarget == 2) {
                            coinPos += dragAmount
                        }
                    },
                    onDragEnd = { selectedTarget = 0 },
                    onDragCancel = { selectedTarget = 0 }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val margin = 24f

            val pockets = listOf(
                Offset(margin, margin),
                Offset(w - margin, margin),
                Offset(margin, h - margin),
                Offset(w - margin, h - margin)
            )

            pockets.forEach { p ->
                drawCircle(color = pocketColor, radius = 22f, center = p)
                drawCircle(color = Color(0x5500E5FF), radius = 22f, center = p, style = Stroke(width = 2f))
            }

            drawCircle(color = Color(0x22FFFFFF), radius = 40f, center = Offset(w / 2, h / 2), style = Stroke(width = 2f))
            drawCircle(color = Color(0x33FF1744), radius = 10f, center = Offset(w / 2, h / 2))

            if (isLinesOn) {
                var targetPocket = pockets[0]
                var minD = Float.MAX_VALUE
                pockets.forEach { p ->
                    val d = hypot(p.x - coinPos.x, p.y - coinPos.y)
                    if (d < minD) {
                        minD = d
                        targetPocket = p
                    }
                }

                // 1. Striker -> Coin Line
                drawLine(
                    color = laserColor.copy(alpha = 0.35f),
                    start = strikerPos,
                    end = coinPos,
                    strokeWidth = strokeWidth * 2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = laserColor,
                    start = strikerPos,
                    end = coinPos,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // 2. Coin -> Pocket Line
                drawLine(
                    color = laserColor.copy(alpha = 0.35f),
                    start = coinPos,
                    end = targetPocket,
                    strokeWidth = strokeWidth * 2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = laserColor,
                    start = coinPos,
                    end = targetPocket,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // 3. 3-Cushion Bank Reflection Lines
                if (isDualBank) {
                    val c1 = Offset(w - margin, (coinPos.y * 0.4f).coerceIn(margin, h - margin))
                    val c2 = Offset((w * 0.5f).coerceIn(margin, w - margin), margin)
                    val c3 = Offset(margin, (h * 0.4f).coerceIn(margin, h - margin))

                    // Cushion 1 (Gold)
                    drawLine(
                        color = Color(0xFFFFD700),
                        start = strikerPos,
                        end = c1,
                        strokeWidth = strokeWidth * 0.9f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = Color(0xFFFFD700), radius = 6f, center = c1)

                    // Cushion 2 (Amber)
                    drawLine(
                        color = Color(0xFFFF9100),
                        start = c1,
                        end = c2,
                        strokeWidth = strokeWidth * 0.8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = Color(0xFFFF9100), radius = 6f, center = c2)

                    // Cushion 3 (Purple)
                    drawLine(
                        color = Color(0xFFD500F9),
                        start = c2,
                        end = c3,
                        strokeWidth = strokeWidth * 0.7f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = Color(0xFFD500F9), radius = 6f, center = c3)
                }

                // Dynamic Pulsating Target Lock Reticle on Destination Pocket
                val pulseRadius = 24f + (pulseScale * 22f)
                val pulseAlpha = ((1f - pulseScale) * 0.8f).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(0xFF00E676).copy(alpha = pulseAlpha),
                    radius = pulseRadius,
                    center = targetPocket,
                    style = Stroke(width = 3f)
                )

                drawCircle(
                    color = Color(0xFF00E676),
                    radius = 24f,
                    center = targetPocket,
                    style = Stroke(width = 3.5f)
                )

                // Reticle Crosshairs
                drawLine(
                    color = Color(0xFF00E676),
                    start = Offset(targetPocket.x - 30f, targetPocket.y),
                    end = Offset(targetPocket.x + 30f, targetPocket.y),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color(0xFF00E676),
                    start = Offset(targetPocket.x, targetPocket.y - 30f),
                    end = Offset(targetPocket.x, targetPocket.y + 30f),
                    strokeWidth = 2f
                )
            }

            drawCircle(color = Color(0xFFFFD700), radius = 18f, center = strikerPos)
            drawCircle(color = Color.White, radius = 18f, center = strikerPos, style = Stroke(width = 2.5f))

            drawCircle(color = Color(0xFFFF1744), radius = 14f, center = coinPos)
            drawCircle(color = Color.White, radius = 14f, center = coinPos, style = Stroke(width = 2f))
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0x22FFFFFF),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x17FFFFFF))
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
    ) {
        content()
    }
}

fun formatCountdown(millis: Long): String {
    val totalSec = millis / 1000
    val days = totalSec / (24 * 3600)
    val hours = (totalSec % (24 * 3600)) / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return String.format("%02dd : %02dh : %02dm : %02ds", days, hours, minutes, seconds)
}
