package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Transparent interactive overlay canvas that renders laser aim lines, rebound rays,
 * AI Gem-Vision target locks, and touch-interactive calibration anchors over any game.
 * Optimized with Choreographer and Hardware Acceleration for 120 FPS fluid rendering.
 */
class AimOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isRenderingLoopActive = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isAttachedToWindow && config.isEnabled && isRenderingLoopActive) {
                invalidate()
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    var config = AimEngineConfig()
        set(value) {
            val prevFps = field.is120FpsEnabled
            field = value
            updatePaints()
            if (value.is120FpsEnabled != prevFps) {
                if (value.is120FpsEnabled) start120FpsLoop() else stop120FpsLoop()
            }
            invalidate()
        }

    var strikerPos = PointF(300f, 1200f)
    var coinPos = PointF(540f, 800f)

    var isInteractiveHandlesVisible: Boolean = true
    var aiStatusText: String = "⚡ GEMINI 2.5 FLASH AI: ACTIVE"
    var isAiScanning: Boolean = false

    private var activeTouchTarget: Int = 0 // 0 = none, 1 = striker, 2 = coin

    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val reboundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        isFakeBoldText = true
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0081220")
        style = Paint.Style.FILL
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updatePaints()
    }

    fun start120FpsLoop() {
        if (!isRenderingLoopActive && config.is120FpsEnabled) {
            isRenderingLoopActive = true
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun stop120FpsLoop() {
        isRenderingLoopActive = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (config.is120FpsEnabled) {
            start120FpsLoop()
        }
    }

    override fun onDetachedFromWindow() {
        stop120FpsLoop()
        super.onDetachedFromWindow()
    }

    private fun updatePaints() {
        laserPaint.color = config.laserColor
        laserPaint.strokeWidth = config.strokeWidth
        if (config.isDotted) {
            laserPaint.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
        } else {
            laserPaint.pathEffect = null
        }

        // Outer glow
        glowPaint.color = (config.laserColor and 0x00FFFFFF) or 0x55000000
        glowPaint.strokeWidth = config.strokeWidth * 2.8f

        // Rebound line paint (dashed gold/green)
        reboundPaint.color = Color.parseColor("#FFFFD700")
        reboundPaint.strokeWidth = config.strokeWidth * 0.9f
        reboundPaint.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)

        badgeBorderPaint.color = config.laserColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            // Position defaults gracefully inside screen
            strikerPos.set(w * 0.5f, h * 0.72f)
            coinPos.set(w * 0.5f, h * 0.45f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!config.isEnabled) return

        val w = width.toFloat().takeIf { it > 0 } ?: 1080f
        val h = height.toFloat().takeIf { it > 0 } ?: 1920f

        val trajectory = AimEngine.calculateTrajectory(
            striker = strikerPos,
            coin = coinPos,
            boardWidth = w,
            boardHeight = h,
            config = config
        )

        val currentTime = System.currentTimeMillis()
        val pulseFraction = ((currentTime % 1400L) / 1400f) // 0.0 to 1.0 smooth cycle

        // 1. Draw Striker 3-Cushion Bank Rebound Lines
        if (config.is3CushionEnabled || config.isDualReboundEnabled) {
            val strikerPoints = trajectory.striker3CushionPoints
            if (strikerPoints.size >= 2) {
                for (i in 0 until strikerPoints.size - 1) {
                    val p1 = strikerPoints[i]
                    val p2 = strikerPoints[i + 1]
                    
                    // Progressive color and opacity for cushion bounces (C1 Gold, C2 Orange, C3 Purple)
                    val cushionPaint = Paint(reboundPaint).apply {
                        color = when (i) {
                            0 -> Color.parseColor("#FFFFD700") // C1: Neon Gold
                            1 -> Color.parseColor("#FFFF9100") // C2: Electric Amber
                            else -> Color.parseColor("#FFD500F9") // C3: Cyber Purple
                        }
                        strokeWidth = (config.strokeWidth * (0.95f - i * 0.15f)).coerceAtLeast(3f)
                    }
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, cushionPaint)

                    // Draw Cushion Impact Node & Label
                    if (i > 0 && i < strikerPoints.size) {
                        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = cushionPaint.color
                            style = Paint.Style.FILL
                        }
                        canvas.drawCircle(p1.x, p1.y, 8f, nodePaint)
                        canvas.drawCircle(p1.x, p1.y, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = cushionPaint.color
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                        })
                    }
                }
            }

            // 2. Draw Coin 3-Cushion Bank Rebound Lines
            val coinPoints = trajectory.coin3CushionPoints
            if (coinPoints.size >= 2) {
                for (i in 0 until coinPoints.size - 1) {
                    val p1 = coinPoints[i]
                    val p2 = coinPoints[i + 1]
                    val cushionPaint = Paint(reboundPaint).apply {
                        color = when (i) {
                            0 -> Color.parseColor("#FF00E676") // C1: Neon Green
                            1 -> Color.parseColor("#FF00E5FF") // C2: Cyan
                            else -> Color.parseColor("#FFFFD700") // C3: Gold
                        }
                        strokeWidth = (config.strokeWidth * (0.9f - i * 0.12f)).coerceAtLeast(3f)
                    }
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, cushionPaint)

                    if (i > 0 && i < coinPoints.size) {
                        canvas.drawCircle(p1.x, p1.y, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = cushionPaint.color
                            style = Paint.Style.FILL
                        })
                    }
                }
            }
        }

        // 3. Draw Main Laser Glow and Solid Trajectory (Striker -> Coin)
        val s = trajectory.strikerPos
        val c = trajectory.coinPos
        canvas.drawLine(s.x, s.y, c.x, c.y, glowPaint)
        canvas.drawLine(s.x, s.y, c.x, c.y, laserPaint)

        // 4. Draw Deflection Laser (Coin -> Target Pocket) if Auto Pocket Prediction is enabled
        val p = trajectory.targetPocket
        if (config.isAutoPocketPredictionEnabled) {
            canvas.drawLine(c.x, c.y, p.x, p.y, glowPaint)
            canvas.drawLine(c.x, c.y, p.x, p.y, laserPaint)

            // 5. High-Precision Pulsating Target Lock Reticle on Destination Pocket
            val lockColor = if (trajectory.isPocketLocked) Color.parseColor("#00E676") else config.laserColor

            // Animated Expanding Pulse Ring 1
            val pulseRadius1 = config.pocketRadius + (pulseFraction * 26f)
            val pulseAlpha1 = ((1f - pulseFraction) * 200).toInt().coerceIn(0, 255)
            val pulsePaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lockColor and 0x00FFFFFF or (pulseAlpha1 shl 24)
                style = Paint.Style.STROKE
                strokeWidth = 3.5f
            }
            canvas.drawCircle(p.x, p.y, pulseRadius1, pulsePaint1)

            // Animated Expanding Pulse Ring 2 (Secondary Phase)
            val pulseFraction2 = (pulseFraction + 0.5f) % 1.0f
            val pulseRadius2 = config.pocketRadius + (pulseFraction2 * 26f)
            val pulseAlpha2 = ((1f - pulseFraction2) * 140).toInt().coerceIn(0, 255)
            val pulsePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lockColor and 0x00FFFFFF or (pulseAlpha2 shl 24)
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            canvas.drawCircle(p.x, p.y, pulseRadius2, pulsePaint2)

            // Core Solid Target Lock Ring
            val mainPocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lockColor
                style = Paint.Style.STROKE
                strokeWidth = 4.5f
            }
            canvas.drawCircle(p.x, p.y, config.pocketRadius, mainPocketPaint)

            // Rotating Precision Reticle Crosshair Ticks
            val tickRadius = config.pocketRadius + 10f
            val angleOffset = (currentTime % 3600L) / 3600f * 360f
            for (angleStep in 0 until 4) {
                val rad = Math.toRadians((angleStep * 90.0 + angleOffset))
                val cosA = Math.cos(rad).toFloat()
                val sinA = Math.sin(rad).toFloat()
                val x1 = p.x + cosA * (config.pocketRadius - 8f)
                val y1 = p.y + sinA * (config.pocketRadius - 8f)
                val x2 = p.x + cosA * (tickRadius + 6f)
                val y2 = p.y + sinA * (tickRadius + 6f)
                canvas.drawLine(x1, y1, x2, y2, mainPocketPaint)
            }

            // Target Pocket Lock Tag
            val lockTag = "🎯 LOCK ${trajectory.lockScorePercent}%"
            val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lockColor
                textSize = 22f
                isFakeBoldText = true
            }
            val tagWidth = tagTextPaint.measureText(lockTag)
            val tagX = p.x - tagWidth / 2f
            val tagY = p.y + config.pocketRadius + 28f
            canvas.drawText(lockTag, tagX, tagY, tagTextPaint)
        }

        // 6. Draw Interactive Striker & Coin Handles
        if (isInteractiveHandlesVisible) {
            // Striker Handle
            val strikerHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#AAFFD700")
                style = Paint.Style.FILL
            }
            val strikerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawCircle(s.x, s.y, config.strikerRadius, strikerHandlePaint)
            canvas.drawCircle(s.x, s.y, config.strikerRadius, strikerBorderPaint)

            // Coin Handle with AI Target Crosshair
            val coinHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CCFF1744")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(c.x, c.y, config.coinRadius, coinHandlePaint)
            canvas.drawCircle(c.x, c.y, config.coinRadius, strikerBorderPaint)
        }

        // 7. Draw HUD Badge with Angle, Pocket and Gemini AI status
        if (config.showAngleHud) {
            val badgeText = "🎯 ${trajectory.pocketName} | ${trajectory.angleDegrees.toInt()}° | 3-Cushion: ON | $aiStatusText"
            val textWidth = textPaint.measureText(badgeText)
            val badgeX = (s.x + c.x) / 2f - textWidth / 2f - 20f
            val badgeY = (s.y + c.y) / 2f - 30f
            val badgeRect = RectF(badgeX, badgeY - 34f, badgeX + textWidth + 40f, badgeY + 16f)

            canvas.drawRoundRect(badgeRect, 16f, 16f, badgeBgPaint)
            canvas.drawRoundRect(badgeRect, 16f, 16f, badgeBorderPaint)
            canvas.drawText(badgeText, badgeX + 20f, badgeY, textPaint)
        }

        // Continuously animate the pulsating target lock reticle smoothly
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractiveHandlesVisible || !config.isEnabled) {
            return false
        }

        val touchX = event.x
        val touchY = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val distToStriker = hypot(touchX - strikerPos.x, touchY - strikerPos.y)
                val distToCoin = hypot(touchX - coinPos.x, touchY - coinPos.y)

                if (distToStriker < config.strikerRadius * 2.2f) {
                    activeTouchTarget = 1
                    return true
                } else if (distToCoin < config.coinRadius * 2.8f) {
                    activeTouchTarget = 2
                    return true
                }
                activeTouchTarget = 0
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTouchTarget == 1) {
                    strikerPos.set(touchX, touchY)
                    invalidate()
                    return true
                } else if (activeTouchTarget == 2) {
                    coinPos.set(touchX, touchY)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeTouchTarget = 0
            }
        }
        return super.onTouchEvent(event)
    }

    fun applyAiDetectionResult(result: AiAimDetectionResult) {
        val w = width.toFloat().takeIf { it > 0 } ?: 1080f
        val h = height.toFloat().takeIf { it > 0 } ?: 1920f

        strikerPos.set(result.strikerXPercent * w, result.strikerYPercent * h)
        coinPos.set(result.targetCoinXPercent * w, result.targetCoinYPercent * h)
        aiStatusText = "AI: ${result.targetPocket} (${result.shotAngleDegrees.toInt()}°) Pwr:${result.recommendedPowerPercent}%"
        invalidate()
    }

    fun resetPositions() {
        val w = width.toFloat().takeIf { it > 0 } ?: 1080f
        val h = height.toFloat().takeIf { it > 0 } ?: 1920f
        strikerPos.set(w * 0.5f, h * 0.72f)
        coinPos.set(w * 0.5f, h * 0.45f)
        aiStatusText = "⚡ GEMINI AI CALIBRATED"
        invalidate()
    }
}
