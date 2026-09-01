package com.example

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FloatingAimService runs as a foreground service rendering:
 * 1. Interactive 120 FPS overlay with Queen + Cover AI & Dynamic Power Gauge
 * 2. Draggable floating control bubble with single-tap/double-tap gesture detection
 * 3. Expanded Quick Settings HUD supporting In-Game HUD Line Style Customizer
 * 4. Opponent Turn standby / battery saver optimization (0% idle CPU)
 * 5. Automated anti-ban humanized gesture dispatch via Accessibility API
 */
class FloatingAimService : Service() {

    companion object {
        const val CHANNEL_ID = "RakibAiAimServiceChannel"
        const val NOTIFICATION_ID = 1001
        const val CARROM_PACKAGE_NAME = "com.miniclip.carrom"

        val isServiceRunning = MutableStateFlow(false)
    }

    private lateinit var windowManager: WindowManager
    private var aimOverlayCanvasView: AimOverlayView? = null
    private var floatingBubbleView: View? = null

    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null

    private var isPopupExpanded = false
    private var isAutoPlayActive = false
    private var isQueenPriorityActive = true
    private var isOpponentTurnActive = false
    private var currentConfig = AimEngineConfig(
        isEnabled = true,
        lineMode = LineRenderMode.LASER_PRO,
        lineStyle = AimLineStyle.LASER_GLOW,
        isAutoPlayEnabled = false,
        isQueenPriorityEnabled = true,
        isStealthMode = true,
        is120FpsEnabled = true,
        isPerformanceSavingActive = true
    )

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var autoPlayJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()

        createAimOverlayCanvas()
        createFloatingControlBubble()
        startAutoPlayWatcherLoop()

        isServiceRunning.value = true
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rakib AI ultra Floating Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zero-Miss AI Laser Aim Line & Auto-Play Overlay active"
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ Rakib AI ultra Engine Active")
            .setContentText("Zero-Miss AI Trajectory & Queen+Cover Matrix Running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createAimOverlayCanvas() {
        aimOverlayCanvasView = AimOverlayView(this).apply {
            config = currentConfig
            isInteractiveHandlesVisible = true
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (currentConfig.isStealthMode) {
            flags = flags or WindowManager.LayoutParams.FLAG_SECURE
        }

        overlayLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(aimOverlayCanvasView, overlayLayoutParams)
        } catch (e: Exception) {
            Toast.makeText(this, "Overlay permission error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createFloatingControlBubble() {
        val inflater = LayoutInflater.from(this)
        floatingBubbleView = inflater.inflate(R.layout.layout_floating_widget, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 350
        }

        setupBubbleControls(floatingBubbleView!!)

        try {
            windowManager.addView(floatingBubbleView, bubbleLayoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val idleHandler = Handler(Looper.getMainLooper())
    private var isBubbleDimmed = false
    private var snapAnimator: ValueAnimator? = null

    private fun performTactileHaptic(view: View? = null, isHeavy: Boolean = false) {
        try {
            if (view != null) {
                val feedback = if (isHeavy) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
                view.performHapticFeedback(feedback)
            }
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val duration = if (isHeavy) 45L else 22L
                    val amplitude = if (isHeavy) VibrationEffect.DEFAULT_AMPLITUDE else 160
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(if (isHeavy) 45L else 22L)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetIdleDimTimer(view: View) {
        if (isBubbleDimmed) {
            view.animate().alpha(1.0f).setDuration(220).start()
            isBubbleDimmed = false
        }
        idleHandler.removeCallbacksAndMessages(null)
        idleHandler.postDelayed({
            if (!isPopupExpanded && floatingBubbleView != null) {
                view.animate().alpha(0.40f).setDuration(400).start()
                isBubbleDimmed = true
            }
        }, 3000L)
    }

    private fun setupBubbleControls(view: View) {
        val bubbleIcon = view.findViewById<ImageButton>(R.id.btn_bubble_icon)
        val popupPanel = view.findViewById<LinearLayout>(R.id.panel_expanded_menu)
        val btnCycleGameMode = view.findViewById<Button>(R.id.btn_cycle_game_mode)
        val btnToggleBaselineGuide = view.findViewById<Button>(R.id.btn_toggle_baseline_guide)
        val btnToggleAutoPlay = view.findViewById<Button>(R.id.btn_toggle_autoplay)
        val btnToggleQueen = view.findViewById<Button>(R.id.btn_toggle_queen)
        val btnCycleTargetFocus = view.findViewById<Button>(R.id.btn_cycle_target_focus)
        val btnToggleLineMode = view.findViewById<Button>(R.id.btn_toggle_line_mode)
        val btnToggleOpponentTurn = view.findViewById<Button>(R.id.btn_toggle_opponent_turn)
        val btnLaunchCarrom = view.findViewById<Button>(R.id.btn_launch_carrom)
        val btnTriggerAutoShot = view.findViewById<Button>(R.id.btn_trigger_auto_shot)
        val btnToggleStealth = view.findViewById<Button>(R.id.btn_toggle_stealth)
        val btnToggleLines = view.findViewById<Button>(R.id.btn_toggle_lines)

        val btnThicknessDec = view.findViewById<Button>(R.id.btn_thickness_decrease)
        val btnThicknessInc = view.findViewById<Button>(R.id.btn_thickness_increase)
        val tvLineThickness = view.findViewById<TextView>(R.id.tv_line_thickness)

        val btnStyleRgbChroma = view.findViewById<Button>(R.id.btn_style_rgb_chroma)
        val btnStyleSolidClassic = view.findViewById<Button>(R.id.btn_style_solid_classic)
        val btnStyleLaser = view.findViewById<Button>(R.id.btn_style_laser_glow)
        val btnStyleNeon = view.findViewById<Button>(R.id.btn_style_solid_neon)
        val btnStyleCyber = view.findViewById<Button>(R.id.btn_style_dual_cyber)
        val btnStyleGreen = view.findViewById<Button>(R.id.btn_style_cyber_green)
        val btnStyleGold = view.findViewById<Button>(R.id.btn_style_gold_royal)

        val btnClose = view.findViewById<Button>(R.id.btn_close_floating)
        val tvAiStatus = view.findViewById<TextView>(R.id.tv_widget_ai_status)

        // Continuous Live Cloud AI Matrix & Telemetry Sync Observer
        serviceScope.launch {
            CloudAiConnectionManager.connectionState.collect { cloudState ->
                withContext(Dispatchers.Main) {
                    tvAiStatus.text = "${cloudState.livePingBadge}\n${currentConfig.gameMode.badge} • ${currentConfig.lineStyle.label}"
                }
            }
        }

        // Initialize smart idle dimming (3s timer)
        resetIdleDimTimer(view)

        fun togglePopup() {
            performTactileHaptic(bubbleIcon, false)
            isPopupExpanded = !isPopupExpanded
            popupPanel.visibility = if (isPopupExpanded) View.VISIBLE else View.GONE
            resetIdleDimTimer(view)
        }

        fun toggleQuickHideOverlay() {
            performTactileHaptic(bubbleIcon, true)
            val newEnabled = !currentConfig.isEnabled
            currentConfig = currentConfig.copy(isEnabled = newEnabled)
            aimOverlayCanvasView?.config = currentConfig
            aimOverlayCanvasView?.visibility = if (newEnabled) View.VISIBLE else View.GONE
            btnToggleLines.text = if (newEnabled) "Overlay: ON" else "Overlay: OFF"
            btnToggleLines.setBackgroundColor(if (newEnabled) Color.parseColor("#00838F") else Color.parseColor("#455A64"))
            Toast.makeText(
                this,
                if (newEnabled) "✨ Overlay Visible (120 FPS)" else "🛡️ Quick-Hide / Stealth Mode (Overlay Hidden)",
                Toast.LENGTH_SHORT
            ).show()
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                togglePopup()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleQuickHideOverlay()
                return true
            }
        })

        fun snapBubbleToEdge(currentX: Int) {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val bubbleWidth = bubbleIcon.width.takeIf { it > 0 } ?: (58 * displayMetrics.density).toInt()
            val leftTarget = (12 * displayMetrics.density).toInt()
            val rightTarget = screenWidth - bubbleWidth - (12 * displayMetrics.density).toInt()
            val targetX = if (currentX + bubbleWidth / 2 < screenWidth / 2) leftTarget else rightTarget

            snapAnimator?.cancel()
            snapAnimator = ValueAnimator.ofInt(currentX, targetX).apply {
                duration = 320
                interpolator = OvershootInterpolator(1.1f)
                addUpdateListener { animator ->
                    val animatedX = animator.animatedValue as Int
                    bubbleLayoutParams?.x = animatedX
                    bubbleLayoutParams?.let {
                        if (floatingBubbleView != null) {
                            try {
                                windowManager.updateViewLayout(floatingBubbleView, it)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                start()
            }
        }

        bubbleIcon.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                resetIdleDimTimer(view)
                gestureDetector.onTouchEvent(event)

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        snapAnimator?.cancel()
                        initialX = bubbleLayoutParams?.x ?: 0
                        initialY = bubbleLayoutParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            isDragging = true
                            bubbleLayoutParams?.x = initialX + dx
                            bubbleLayoutParams?.y = initialY + dy
                            bubbleLayoutParams?.let {
                                try {
                                    windowManager.updateViewLayout(floatingBubbleView, it)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            val currX = bubbleLayoutParams?.x ?: 0
                            snapBubbleToEdge(currX)
                            performTactileHaptic(bubbleIcon, false)
                        }
                        return true
                    }
                }
                return false
            }
        })

        // =========================================================================
        // 0. MULTI-GAME MODE SWITCHER (Disc Pool / Classic Carrom / Freestyle)
        // =========================================================================
        fun updateGameModeButton() {
            btnCycleGameMode.text = "🎮 Mode: ${currentConfig.gameMode.badge} 🔄"
            when (currentConfig.gameMode) {
                GameMode.DISC_POOL -> {
                    btnCycleGameMode.setBackgroundColor(Color.parseColor("#00E5FF"))
                    btnCycleGameMode.setTextColor(Color.parseColor("#060B13"))
                }
                GameMode.CLASSIC_CARROM -> {
                    btnCycleGameMode.setBackgroundColor(Color.parseColor("#FFD700"))
                    btnCycleGameMode.setTextColor(Color.parseColor("#060B13"))
                }
                GameMode.FREESTYLE -> {
                    btnCycleGameMode.setBackgroundColor(Color.parseColor("#E040FB"))
                    btnCycleGameMode.setTextColor(Color.WHITE)
                }
            }
        }
        updateGameModeButton()

        btnCycleGameMode.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            val allModes = GameMode.values()
            val nextIndex = (currentConfig.gameMode.ordinal + 1) % allModes.size
            val nextMode = allModes[nextIndex]

            currentConfig = currentConfig.copy(gameMode = nextMode)
            aimOverlayCanvasView?.config = currentConfig
            updateGameModeButton()

            tvAiStatus.text = "Mode: ${nextMode.label} (${nextMode.description})"
            Toast.makeText(this, "🎮 Game Mode: ${nextMode.label}\n${nextMode.description}", Toast.LENGTH_SHORT).show()
        }

        // =========================================================================
        // 0.5. STRIKER BASELINE POSITION GUIDE TOGGLE
        // =========================================================================
        fun updateBaselineGuideButton() {
            btnToggleBaselineGuide.text = if (currentConfig.showBaselineGuide) "📏 Baseline Guide: ON 🟢" else "📏 Baseline Guide: OFF ⚪"
            btnToggleBaselineGuide.setBackgroundColor(if (currentConfig.showBaselineGuide) Color.parseColor("#1B5E20") else Color.parseColor("#37474F"))
            btnToggleBaselineGuide.setTextColor(Color.WHITE)
        }
        updateBaselineGuideButton()

        btnToggleBaselineGuide.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            val newGuideState = !currentConfig.showBaselineGuide
            currentConfig = currentConfig.copy(showBaselineGuide = newGuideState)
            aimOverlayCanvasView?.config = currentConfig
            updateBaselineGuideButton()

            Toast.makeText(
                this,
                if (newGuideState) "📏 Baseline Sweet-Spot Alignment Guide ACTIVE" else "Baseline Guide Hidden",
                Toast.LENGTH_SHORT
            ).show()
        }

        // =========================================================================
        // 1. AUTO-PLAY / AUTO-SHOT TOGGLE
        // =========================================================================
        btnToggleAutoPlay.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            val isAccessibilityGranted = CarromAutoPlayService.isAccessibilitySettingsOn(this)
            if (!isAccessibilityGranted) {
                Toast.makeText(
                    this,
                    "⚠️ Please enable 'Rakib AI Aim' in Accessibility Settings first.",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return@setOnClickListener
            }

            isAutoPlayActive = !isAutoPlayActive
            currentConfig = currentConfig.copy(isAutoPlayEnabled = isAutoPlayActive)
            aimOverlayCanvasView?.config = currentConfig
            aimOverlayCanvasView?.isAutoPlayActive = isAutoPlayActive
            aimOverlayCanvasView?.wakeRenderingEngine()
            CarromAutoPlayService.isAutoPlayActive.value = isAutoPlayActive

            btnToggleAutoPlay.text = if (isAutoPlayActive) "🤖 Auto-Play Shot: ON 🟢" else "🤖 Auto-Play Shot: OFF ⚪"
            btnToggleAutoPlay.setBackgroundColor(if (isAutoPlayActive) Color.parseColor("#00E676") else Color.parseColor("#37474F"))
            btnToggleAutoPlay.setTextColor(if (isAutoPlayActive) Color.parseColor("#060B13") else Color.WHITE)

            tvAiStatus.text = if (isAutoPlayActive) "Auto-Play: ACTIVE (Dynamic Power & Anti-Ban)" else "Physics: Zero-Miss Ray Engine Active"

            Toast.makeText(
                this,
                if (isAutoPlayActive) "🤖 Auto-Play Shot Engine ACTIVATED!" else "Auto-Play Shot Paused",
                Toast.LENGTH_SHORT
            ).show()
        }

        // =========================================================================
        // 2. QUEEN + COVER AUTO-PRIORITY AI
        // =========================================================================
        btnToggleQueen.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            isQueenPriorityActive = !isQueenPriorityActive
            currentConfig = currentConfig.copy(isQueenPriorityEnabled = isQueenPriorityActive)
            aimOverlayCanvasView?.config = currentConfig

            btnToggleQueen.text = if (isQueenPriorityActive) "👑 Queen+Cover Priority: ON 🟢" else "👑 Queen+Cover Priority: OFF ⚪"
            btnToggleQueen.setBackgroundColor(if (isQueenPriorityActive) Color.parseColor("#FFD700") else Color.parseColor("#455A64"))
            btnToggleQueen.setTextColor(if (isQueenPriorityActive) Color.parseColor("#060B13") else Color.WHITE)

            Toast.makeText(
                this,
                if (isQueenPriorityActive) "👑 Queen + Cover 2-Shot Strategy Locked!" else "Queen Priority Disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // =========================================================================
        // 2.5. MULTI-TARGET CYCLE FOCUS TOGGLE
        // =========================================================================
        fun updateTargetFocusButton() {
            btnCycleTargetFocus.text = "🎯 Focus: ${currentConfig.targetFocusMode.label} 🔄"
            when (currentConfig.targetFocusMode) {
                TargetFocusMode.EASIEST_PUCK -> {
                    btnCycleTargetFocus.setBackgroundColor(Color.parseColor("#00838F"))
                    btnCycleTargetFocus.setTextColor(Color.WHITE)
                }
                TargetFocusMode.QUEEN -> {
                    btnCycleTargetFocus.setBackgroundColor(Color.parseColor("#FFD700"))
                    btnCycleTargetFocus.setTextColor(Color.parseColor("#060B13"))
                }
                TargetFocusMode.COMBO_3BODY -> {
                    btnCycleTargetFocus.setBackgroundColor(Color.parseColor("#FF6D00"))
                    btnCycleTargetFocus.setTextColor(Color.WHITE)
                }
                TargetFocusMode.BANK_SHOT -> {
                    btnCycleTargetFocus.setBackgroundColor(Color.parseColor("#D500F9"))
                    btnCycleTargetFocus.setTextColor(Color.WHITE)
                }
            }
        }
        updateTargetFocusButton()

        btnCycleTargetFocus.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            val allFocusModes = TargetFocusMode.values()
            val nextIndex = (currentConfig.targetFocusMode.ordinal + 1) % allFocusModes.size
            val nextFocusMode = allFocusModes[nextIndex]

            currentConfig = currentConfig.copy(targetFocusMode = nextFocusMode)
            aimOverlayCanvasView?.config = currentConfig
            updateTargetFocusButton()

            Toast.makeText(this, "🎯 Target Focus: ${nextFocusMode.label}", Toast.LENGTH_SHORT).show()
        }

        // =========================================================================
        // 3. ADVANCED CARROM SHOT & LINE MODE TOGGLE
        // =========================================================================
        fun updateLineModeButton() {
            btnToggleLineMode.text = "Mode: ${currentConfig.lineMode.badge}"
            when (currentConfig.lineMode) {
                LineRenderMode.DIRECT -> {
                    btnToggleLineMode.setBackgroundColor(Color.parseColor("#37474F"))
                    btnToggleLineMode.setTextColor(Color.WHITE)
                }
                LineRenderMode.BANK_1_CUSHION -> {
                    btnToggleLineMode.setBackgroundColor(Color.parseColor("#D50000"))
                    btnToggleLineMode.setTextColor(Color.WHITE)
                }
                LineRenderMode.BANK_2_CUSHION -> {
                    btnToggleLineMode.setBackgroundColor(Color.parseColor("#FF6D00"))
                    btnToggleLineMode.setTextColor(Color.WHITE)
                }
                LineRenderMode.BANK_3_CUSHION -> {
                    btnToggleLineMode.setBackgroundColor(Color.parseColor("#AA00FF"))
                    btnToggleLineMode.setTextColor(Color.WHITE)
                }
                LineRenderMode.KISS_SHOT -> {
                    btnToggleLineMode.setBackgroundColor(Color.parseColor("#FFD600"))
                    btnToggleLineMode.setTextColor(Color.parseColor("#060B13"))
                }
                LineRenderMode.COMBO_3_BODY -> {
                    btnToggleLineMode.setBackgroundColor(Color.parseColor("#FF6D00"))
                    btnToggleLineMode.setTextColor(Color.WHITE)
                }
                LineRenderMode.CUT_SHOT -> {
                    btnToggleLineMode.setBackgroundColor(Color.parseColor("#00C853"))
                    btnToggleLineMode.setTextColor(Color.parseColor("#060B13"))
                }
                LineRenderMode.BACK_SLICE -> {
                    btnToggleLineMode.setBackgroundColor(Color.parseColor("#0091EA"))
                    btnToggleLineMode.setTextColor(Color.WHITE)
                }
                LineRenderMode.BREAK_SHOT -> {
                    btnToggleLineMode.setBackgroundColor(Color.parseColor("#FF6D00"))
                    btnToggleLineMode.setTextColor(Color.parseColor("#060B13"))
                }
                LineRenderMode.LASER_PRO -> {
                    btnToggleLineMode.setBackgroundColor(Color.parseColor("#00E5FF"))
                    btnToggleLineMode.setTextColor(Color.parseColor("#060B13"))
                }
            }
        }
        updateLineModeButton()

        btnToggleLineMode.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            val allModes = LineRenderMode.values()
            val nextIndex = (currentConfig.lineMode.ordinal + 1) % allModes.size
            val nextMode = allModes[nextIndex]

            currentConfig = currentConfig.copy(
                lineMode = nextMode,
                is3CushionEnabled = (nextMode == LineRenderMode.BANK_3_CUSHION || nextMode == LineRenderMode.LASER_PRO)
            )
            aimOverlayCanvasView?.config = currentConfig
            updateLineModeButton()

            Toast.makeText(this, "Shot Mode: ${nextMode.label}", Toast.LENGTH_SHORT).show()
        }

        // =========================================================================
        // 4. OPPONENT TURN STANDBY / BATTERY SAVER (0% CPU)
        // =========================================================================
        btnToggleOpponentTurn.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            isOpponentTurnActive = !isOpponentTurnActive
            aimOverlayCanvasView?.isOpponentTurn = isOpponentTurnActive

            btnToggleOpponentTurn.text = if (isOpponentTurnActive) "▶️ Resume My Turn (120 FPS)" else "⏸️ Opponent Turn (Pause 0% CPU)"
            btnToggleOpponentTurn.setBackgroundColor(if (isOpponentTurnActive) Color.parseColor("#FF9100") else Color.parseColor("#455A64"))

            Toast.makeText(
                this,
                if (isOpponentTurnActive) "⏸️ Paused Overlay for Opponent Turn (Battery Saved)" else "▶️ Resumed 120 FPS Aim Engine!",
                Toast.LENGTH_SHORT
            ).show()
        }

        // =========================================================================
        // 5. LAUNCH CARROM POOL DIRECT INTENT
        // =========================================================================
        btnLaunchCarrom.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            launchCarromPoolApp()
        }

        // =========================================================================
        // 6. TRIGGER INSTANT AUTO STRIKE (DYNAMIC POWER)
        // =========================================================================
        btnTriggerAutoShot.setOnClickListener {
            performTactileHaptic(it, true)
            resetIdleDimTimer(view)
            triggerManualAutoStrike()
        }

        // =========================================================================
        // 7. STEALTH MODE & OVERLAY TOGGLES
        // =========================================================================
        btnToggleStealth.text = if (currentConfig.isStealthMode) "🛡️ Stealth: ON" else "🛡️ Stealth: OFF"
        btnToggleStealth.setBackgroundColor(if (currentConfig.isStealthMode) Color.parseColor("#1B5E20") else Color.parseColor("#455A64"))
        btnToggleStealth.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            val newStealth = !currentConfig.isStealthMode
            currentConfig = currentConfig.copy(isStealthMode = newStealth)
            aimOverlayCanvasView?.config = currentConfig

            overlayLayoutParams?.let { params ->
                if (newStealth) {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_SECURE
                } else {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                }
                aimOverlayCanvasView?.let { overlay ->
                    try {
                        windowManager.updateViewLayout(overlay, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            btnToggleStealth.text = if (newStealth) "🛡️ Stealth: ON" else "🛡️ Stealth: OFF"
            btnToggleStealth.setBackgroundColor(if (newStealth) Color.parseColor("#1B5E20") else Color.parseColor("#455A64"))
            Toast.makeText(
                this,
                if (newStealth) "🛡️ Stealth Mode: ON (Screen capture protected)" else "🛡️ Stealth Mode: OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnToggleLines.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            val newEnabled = !currentConfig.isEnabled
            currentConfig = currentConfig.copy(isEnabled = newEnabled)
            aimOverlayCanvasView?.config = currentConfig
            btnToggleLines.text = if (newEnabled) "Overlay: ON" else "Overlay: OFF"
            btnToggleLines.setBackgroundColor(if (newEnabled) Color.parseColor("#00838F") else Color.parseColor("#455A64"))
        }

        // =========================================================================
        // 7.5. LINE THICKNESS ADJUSTER
        // =========================================================================
        fun updateThicknessDisplay() {
            tvLineThickness.text = "Thickness: ${String.format(java.util.Locale.US, "%.1f", currentConfig.strokeWidth)}dp"
        }
        updateThicknessDisplay()

        btnThicknessDec.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            val newWidth = (currentConfig.strokeWidth - 1.0f).coerceAtLeast(2.0f)
            currentConfig = currentConfig.copy(strokeWidth = newWidth)
            aimOverlayCanvasView?.config = currentConfig
            updateThicknessDisplay()
        }

        btnThicknessInc.setOnClickListener {
            performTactileHaptic(it, false)
            resetIdleDimTimer(view)
            val newWidth = (currentConfig.strokeWidth + 1.0f).coerceAtMost(16.0f)
            currentConfig = currentConfig.copy(strokeWidth = newWidth)
            aimOverlayCanvasView?.config = currentConfig
            updateThicknessDisplay()
        }

        // =========================================================================
        // 8. IN-GAME HUD QUICK LINE STYLE CUSTOMIZER
        // =========================================================================
        fun applyLineStyle(style: AimLineStyle) {
            performTactileHaptic(null, false)
            resetIdleDimTimer(view)
            currentConfig = currentConfig.copy(lineStyle = style)
            aimOverlayCanvasView?.config = currentConfig
            Toast.makeText(this, "🎨 Aim Style: ${style.label}", Toast.LENGTH_SHORT).show()
        }

        btnStyleRgbChroma.setOnClickListener { applyLineStyle(AimLineStyle.RGB_CHROMA) }
        btnStyleSolidClassic.setOnClickListener { applyLineStyle(AimLineStyle.SOLID_CLASSIC) }
        btnStyleLaser.setOnClickListener { applyLineStyle(AimLineStyle.LASER_GLOW) }
        btnStyleNeon.setOnClickListener { applyLineStyle(AimLineStyle.SOLID_NEON) }
        btnStyleCyber.setOnClickListener { applyLineStyle(AimLineStyle.DUAL_GRADIENT) }
        btnStyleGreen.setOnClickListener { applyLineStyle(AimLineStyle.CYBER_GREEN) }
        btnStyleGold.setOnClickListener { applyLineStyle(AimLineStyle.GOLD_CHAMPION) }

        // =========================================================================
        // 9. CLOSE SERVICE
        // =========================================================================
        btnClose.setOnClickListener {
            performTactileHaptic(it, true)
            Toast.makeText(this, "🛑 Rakib AI Floating Engine Stopped", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun launchCarromPoolApp() {
        val pm = packageManager
        var launchIntent = pm.getLaunchIntentForPackage(CARROM_PACKAGE_NAME)
        if (launchIntent == null) {
            try {
                launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$CARROM_PACKAGE_NAME")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(launchIntent)
                Toast.makeText(this, "Opening Play Store for Carrom Disc Pool...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$CARROM_PACKAGE_NAME")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
            }
        } else {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            Toast.makeText(this, "🎮 Launching Carrom Disc Pool...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerManualAutoStrike() {
        val overlay = aimOverlayCanvasView ?: return
        val w = overlay.width.toFloat().takeIf { it > 0 } ?: 1080f
        val h = overlay.height.toFloat().takeIf { it > 0 } ?: 1920f

        val trajectory = AimEngine.calculateTrajectory(
            striker = overlay.strikerPos,
            coin = overlay.coinPos,
            boardWidth = w,
            boardHeight = h,
            config = currentConfig
        )

        if (!CarromAutoPlayService.isAccessibilitySettingsOn(this)) {
            Toast.makeText(this, "⚠️ Enable Accessibility Service in Settings to use Auto Strike.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }

        CarromAutoPlayService.executeAutoShot(
            strikerPos = trajectory.strikerPos,
            aimTargetPos = trajectory.ghostStrikerPos,
            powerPercent = trajectory.recommendedPower
        ) { success ->
            if (success) {
                performTactileHaptic(null, true)
                Toast.makeText(
                    this,
                    "⚡ Shot Dispatched (${trajectory.powerLabel} • ${trajectory.lockScorePercent}% Lock)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startAutoPlayWatcherLoop() {
        autoPlayJob = serviceScope.launch {
            while (true) {
                delay(2200)
                if (isAutoPlayActive && aimOverlayCanvasView != null && !isOpponentTurnActive) {
                    val overlay = aimOverlayCanvasView ?: continue
                    val w = overlay.width.toFloat().takeIf { it > 0 } ?: 1080f
                    val h = overlay.height.toFloat().takeIf { it > 0 } ?: 1920f

                    val trajectory = AimEngine.calculateTrajectory(
                        striker = overlay.strikerPos,
                        coin = overlay.coinPos,
                        boardWidth = w,
                        boardHeight = h,
                        config = currentConfig
                    )

                    // Auto-execute if locked and guaranteed winning trajectory
                    if (trajectory.isGuaranteedWin && CarromAutoPlayService.isAccessibilitySettingsOn(this@FloatingAimService)) {
                        CarromAutoPlayService.executeAutoShot(
                            strikerPos = trajectory.strikerPos,
                            aimTargetPos = trajectory.ghostStrikerPos,
                            powerPercent = trajectory.recommendedPower
                        )
                        delay(2200) // Cooldown between automatic shots
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning.value = false
        autoPlayJob?.cancel()
        autoPlayJob = null

        // Stop all background coroutine calculations and watchers
        try {
            serviceScope.coroutineContext.cancelChildren()
        } catch (_: Exception) {}

        // Cancel any pending animations and timers
        snapAnimator?.cancel()
        snapAnimator = null
        idleHandler.removeCallbacksAndMessages(null)

        // Clean up WindowManager views safely
        if (floatingBubbleView != null) {
            try {
                windowManager.removeView(floatingBubbleView)
            } catch (_: Exception) {}
            floatingBubbleView = null
        }
        if (aimOverlayCanvasView != null) {
            try {
                windowManager.removeView(aimOverlayCanvasView)
            } catch (_: Exception) {}
            aimOverlayCanvasView = null
        }
    }
}
