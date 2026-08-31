package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingAimService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingBubbleView: View? = null
    private var aimOverlayCanvasView: AimOverlayView? = null

    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    private var isPopupExpanded = false
    private var isAiAutoScanActive = true
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var autoScanJob: Job? = null

    private var currentConfig = AimEngineConfig(
        isEnabled = true,
        isDualReboundEnabled = true,
        isAutoPocketPredictionEnabled = true,
        laserColor = Color.parseColor("#00E5FF"),
        strokeWidth = 6f,
        showAngleHud = true
    )

    companion object {
        val isServiceRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
        createAimOverlayView()
        createFloatingControlBubble()
        startAiAutoScanLoop()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "floating_aim_service_channel"
        val channelName = "Carrom AI Aim Assist"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            chan.lightColor = Color.CYAN
            chan.lockscreenVisibility = Notification.VISIBILITY_SECRET
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Carrom AI Aim Engine Active 🎯")
            .setContentText("Gemini 2.5 Flash Vision & Neon Floating Bubble running.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun createAimOverlayView() {
        aimOverlayCanvasView = AimOverlayView(this).apply {
            config = currentConfig
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

    private fun setupBubbleControls(view: View) {
        val bubbleIcon = view.findViewById<ImageButton>(R.id.btn_bubble_icon)
        val popupPanel = view.findViewById<LinearLayout>(R.id.panel_expanded_menu)
        val btnToggleLines = view.findViewById<Button>(R.id.btn_toggle_lines)
        val btnTogglePocket = view.findViewById<Button>(R.id.btn_toggle_pocket_prediction)
        val btnToggle3Cushion = view.findViewById<Button>(R.id.btn_toggle_3cushion)
        val btnToggleStealth = view.findViewById<Button>(R.id.btn_toggle_stealth)
        val btnColorCyan = view.findViewById<Button>(R.id.btn_color_cyan)
        val btnColorGreen = view.findViewById<Button>(R.id.btn_color_green)
        val btnColorGold = view.findViewById<Button>(R.id.btn_color_gold)
        val btnColorPurple = view.findViewById<Button>(R.id.btn_color_purple)
        val tvLaserThicknessLabel = view.findViewById<TextView>(R.id.tv_laser_thickness_label)
        val sbLaserThickness = view.findViewById<SeekBar>(R.id.sb_laser_thickness)
        val btnAiScan = view.findViewById<Button>(R.id.btn_ai_scan)
        val btnClose = view.findViewById<Button>(R.id.btn_close_floating)
        val tvAiStatus = view.findViewById<TextView>(R.id.tv_widget_ai_status)

        // Helper to toggle popup
        fun togglePopup() {
            isPopupExpanded = !isPopupExpanded
            popupPanel.visibility = if (isPopupExpanded) View.VISIBLE else View.GONE
        }

        // GestureDetector for Double-Tap & Single-Tap Detection on Neon-Cyan Bubble
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                togglePopup()
                Toast.makeText(
                    this@FloatingAimService,
                    if (isPopupExpanded) "⚡ AI Quick Settings Opened" else "HUD Popup Closed",
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                togglePopup()
                return true
            }
        })

        // Dragging & Gesture Touch Handler
        bubbleIcon.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                // Pass event to GestureDetector first for tap & double-tap
                gestureDetector.onTouchEvent(event)

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
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
                            bubbleLayoutParams?.let { windowManager.updateViewLayout(floatingBubbleView, it) }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        return true
                    }
                }
                return false
            }
        })

        // 1. Toggle "Hide / Show Laser Lines"
        btnToggleLines.setOnClickListener {
            val newEnabled = !currentConfig.isEnabled
            currentConfig = currentConfig.copy(isEnabled = newEnabled)
            aimOverlayCanvasView?.config = currentConfig
            btnToggleLines.text = if (newEnabled) "Laser Lines: VISIBLE 🟢" else "Laser Lines: HIDDEN ⚪"
            btnToggleLines.setBackgroundColor(if (newEnabled) Color.parseColor("#00838F") else Color.parseColor("#455A64"))
            Toast.makeText(
                this,
                if (newEnabled) "Laser Lines: VISIBLE" else "Laser Lines: HIDDEN",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 2. Toggle "Auto Pocket Prediction"
        btnTogglePocket.setOnClickListener {
            val newPocketPred = !currentConfig.isAutoPocketPredictionEnabled
            currentConfig = currentConfig.copy(isAutoPocketPredictionEnabled = newPocketPred)
            aimOverlayCanvasView?.config = currentConfig
            btnTogglePocket.text = if (newPocketPred) "Auto Pocket Lock: ON 🟢" else "Auto Pocket Lock: OFF ⚪"
            btnTogglePocket.setBackgroundColor(if (newPocketPred) Color.parseColor("#F57F17") else Color.parseColor("#455A64"))
            Toast.makeText(
                this,
                if (newPocketPred) "Target Pocket Lock: ON" else "Target Pocket Lock: OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 3. Toggle "3-Cushion Bank Aim"
        btnToggle3Cushion.setOnClickListener {
            val new3Cushion = !currentConfig.is3CushionEnabled
            currentConfig = currentConfig.copy(
                is3CushionEnabled = new3Cushion,
                isDualReboundEnabled = new3Cushion
            )
            aimOverlayCanvasView?.config = currentConfig
            btnToggle3Cushion.text = if (new3Cushion) "3-Cushion Bank Aim: ON 🟢" else "3-Cushion Bank Aim: OFF ⚪"
            btnToggle3Cushion.setBackgroundColor(if (new3Cushion) Color.parseColor("#4527A0") else Color.parseColor("#455A64"))
            Toast.makeText(
                this,
                if (new3Cushion) "3-Cushion Bank Physics: ACTIVE" else "3-Cushion Bank Physics: OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 4. Toggle "Stealth / Safe Mode"
        btnToggleStealth.text = if (currentConfig.isStealthMode) "🛡️ Stealth Safe Mode: ON 🟢" else "🛡️ Stealth Safe Mode: OFF ⚪"
        btnToggleStealth.setBackgroundColor(if (currentConfig.isStealthMode) Color.parseColor("#1B5E20") else Color.parseColor("#455A64"))
        btnToggleStealth.setOnClickListener {
            val newStealth = !currentConfig.isStealthMode
            currentConfig = currentConfig.copy(isStealthMode = newStealth)
            aimOverlayCanvasView?.config = currentConfig

            // Update WindowManager flags dynamically
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

            btnToggleStealth.text = if (newStealth) "🛡️ Stealth Safe Mode: ON 🟢" else "🛡️ Stealth Safe Mode: OFF ⚪"
            btnToggleStealth.setBackgroundColor(if (newStealth) Color.parseColor("#1B5E20") else Color.parseColor("#455A64"))
            Toast.makeText(
                this,
                if (newStealth) "🛡️ Stealth Safe Mode: ACTIVE (Screen-Capture Bypassed)" else "🛡️ Stealth Safe Mode: OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 4. Laser Color Selectors (Cyan, Green, Gold, Purple)
        fun setLaserColor(colorHex: String, colorName: String) {
            val parsedColor = Color.parseColor(colorHex)
            currentConfig = currentConfig.copy(laserColor = parsedColor)
            aimOverlayCanvasView?.config = currentConfig
            Toast.makeText(this, "Laser Color: $colorName", Toast.LENGTH_SHORT).show()
        }

        btnColorCyan.setOnClickListener { setLaserColor("#00E5FF", "Cyan") }
        btnColorGreen.setOnClickListener { setLaserColor("#00E676", "Green") }
        btnColorGold.setOnClickListener { setLaserColor("#FFD700", "Gold") }
        btnColorPurple.setOnClickListener { setLaserColor("#D500F9", "Purple") }

        // 5. Laser Thickness Slider
        sbLaserThickness.progress = currentConfig.strokeWidth.toInt().coerceIn(2, 16)
        tvLaserThicknessLabel.text = "📏 Laser Thickness: ${currentConfig.strokeWidth.toInt()}px"
        sbLaserThickness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(2)
                tvLaserThicknessLabel.text = "📏 Laser Thickness: ${clamped}px"
                currentConfig = currentConfig.copy(strokeWidth = clamped.toFloat())
                aimOverlayCanvasView?.config = currentConfig
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Trigger Instant AI Gemini Vision Scan
        btnAiScan.setOnClickListener {
            triggerGeminiVisionAnalysis(tvAiStatus)
        }

        // 6. "Stop Engine" Button
        btnClose.setOnClickListener {
            Toast.makeText(this, "🛑 AI Floating Engine Stopped", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun triggerGeminiVisionAnalysis(statusTextView: TextView?) {
        statusTextView?.text = "🧠 AI SCANNING TABLE..."
        Toast.makeText(this, "Gemini 2.5 Flash Vision analyzing board...", Toast.LENGTH_SHORT).show()

        serviceScope.launch {
            val width = aimOverlayCanvasView?.width?.toFloat() ?: 1080f
            val height = aimOverlayCanvasView?.height?.toFloat() ?: 1920f

            val result = GeminiVisionAnalyzer.analyzeCarromBoardFrame(
                bitmap = null,
                boardWidth = width,
                boardHeight = height
            )

            result.onSuccess { detection ->
                aimOverlayCanvasView?.applyAiDetectionResult(detection)
                statusTextView?.text = "🎯 AI Locked: ${detection.targetPocket} (${detection.shotAngleDegrees.toInt()}°)"
                Toast.makeText(
                    this@FloatingAimService,
                    "🎯 AI Locked: ${detection.targetPocket} | Angle: ${detection.shotAngleDegrees.toInt()}° | Power: ${detection.recommendedPowerPercent}%",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                statusTextView?.text = "⚠️ AI Offline (Local Mode)"
            }
        }
    }

    private fun startAiAutoScanLoop() {
        autoScanJob = serviceScope.launch {
            while (true) {
                delay(15000)
                if (isAiAutoScanActive && aimOverlayCanvasView != null) {
                    val width = aimOverlayCanvasView?.width?.toFloat() ?: 1080f
                    val height = aimOverlayCanvasView?.height?.toFloat() ?: 1920f
                    val res = GeminiVisionAnalyzer.analyzeCarromBoardFrame(null, width, height)
                    res.onSuccess {
                        aimOverlayCanvasView?.aiStatusText = "AI 2.5: ${it.targetPocket} (${it.shotAngleDegrees.toInt()}°)"
                        aimOverlayCanvasView?.invalidate()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning.value = false
        autoScanJob?.cancel()
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
