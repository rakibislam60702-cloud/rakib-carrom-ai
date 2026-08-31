package com.example

import android.graphics.Color
import android.graphics.PointF
import kotlin.math.*

/**
 * Data class representing a calculated laser shot trajectory for Carrom Disc Pool.
 */
data class AimTrajectory(
    val strikerPos: PointF,
    val coinPos: PointF,
    val targetPocket: PointF,
    val pocketName: String,
    val directStrikeLine: List<PointF>,
    val coinToPocketLine: List<PointF>,
    val strikerReboundLine: List<PointF>,
    val coinReboundLine: List<PointF>,
    val striker3CushionPoints: List<PointF> = emptyList(),
    val coin3CushionPoints: List<PointF> = emptyList(),
    val angleDegrees: Float,
    val isPocketLocked: Boolean,
    val lockScorePercent: Int = 98
)

/**
 * Settings configuration for the AI Aim Line Engine.
 */
data class AimEngineConfig(
    val isEnabled: Boolean = true,
    val isDualReboundEnabled: Boolean = true,
    val is3CushionEnabled: Boolean = true,
    val isAutoPocketPredictionEnabled: Boolean = true,
    val isStealthMode: Boolean = true,
    val is120FpsEnabled: Boolean = true,
    val laserColor: Int = Color.parseColor("#00E5FF"), // Neon Cyan default
    val strokeWidth: Float = 6f,
    val showAngleHud: Boolean = true,
    val isDotted: Boolean = false,
    val strikerRadius: Float = 36f,
    val coinRadius: Float = 24f,
    val pocketRadius: Float = 42f,
    val maxCushions: Int = 3
)

/**
 * High-precision Carrom Disc Pool AI Trajectory, 3-Cushion Bank Rebound, and Pocket Lock Engine.
 */
object AimEngine {

    /**
     * Calculates the direct and 3-cushion rebound paths from Striker to Coin to Target Pocket
     * within the given board boundaries [boardWidth x boardHeight].
     */
    fun calculateTrajectory(
        striker: PointF,
        coin: PointF,
        boardWidth: Float,
        boardHeight: Float,
        config: AimEngineConfig
    ): AimTrajectory {
        val pocketMargin = 40f
        val pockets = listOf(
            Pair("Top-Left", PointF(pocketMargin, pocketMargin)),
            Pair("Top-Right", PointF(boardWidth - pocketMargin, pocketMargin)),
            Pair("Bottom-Left", PointF(pocketMargin, boardHeight - pocketMargin)),
            Pair("Bottom-Right", PointF(boardWidth - pocketMargin, boardHeight - pocketMargin))
        )

        // Find nearest / optimal pocket relative to coin and striker angle
        var bestPocket = pockets[0].second
        var bestPocketName = pockets[0].first
        var minScore = Float.MAX_VALUE

        for ((name, p) in pockets) {
            val distCoinToPocket = hypot(p.x - coin.x, p.y - coin.y)
            val distStrikerToCoin = hypot(coin.x - striker.x, coin.y - striker.y)
            
            // Angle between striker->coin and coin->pocket
            val v1x = coin.x - striker.x
            val v1y = coin.y - striker.y
            val v2x = p.x - coin.x
            val v2y = p.y - coin.y
            
            val dot = (v1x * v2x + v1y * v2y) / (max(1f, distStrikerToCoin) * max(1f, distCoinToPocket))
            val anglePenalty = (1f - dot) * 120f // Lower penalty for aligned straight-line cuts
            val score = distCoinToPocket + anglePenalty

            if (score < minScore) {
                minScore = score
                bestPocket = p
                bestPocketName = name
            }
        }

        // Direct path from striker to coin
        val directStrikeLine = listOf(PointF(striker.x, striker.y), PointF(coin.x, coin.y))

        // Direct path from coin to pocket
        val coinToPocketLine = listOf(PointF(coin.x, coin.y), PointF(bestPocket.x, bestPocket.y))

        // Angle computation in degrees
        val dx = coin.x - striker.x
        val dy = coin.y - striker.y
        val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val normalizedAngle = (angleDeg + 360f) % 360f

        // Calculate precision 3-cushion bank rebound reflections
        val striker3Cushion = mutableListOf<PointF>()
        val coin3Cushion = mutableListOf<PointF>()

        if (config.is3CushionEnabled || config.isDualReboundEnabled) {
            val cushionCount = if (config.is3CushionEnabled) 3 else 1

            // Striker 3-Cushion Rebound off cushion banks
            val reboundStriker = calculateMultiCushionRebound(
                start = striker,
                direction = PointF(coin.x - striker.x, coin.y - striker.y),
                boardWidth = boardWidth,
                boardHeight = boardHeight,
                maxCushions = cushionCount,
                margin = 25f
            )
            striker3Cushion.addAll(reboundStriker)

            // Coin 3-Cushion Rebound off cushion banks
            val reboundCoin = calculateMultiCushionRebound(
                start = coin,
                direction = PointF(bestPocket.x - coin.x, bestPocket.y - coin.y),
                boardWidth = boardWidth,
                boardHeight = boardHeight,
                maxCushions = cushionCount,
                margin = 25f
            )
            coin3Cushion.addAll(reboundCoin)
        }

        val pocketDist = hypot(bestPocket.x - coin.x, bestPocket.y - coin.y)
        val isLocked = pocketDist < (boardWidth * 0.85f)
        val lockScore = (100 - (pocketDist / boardWidth * 30f).toInt()).coerceIn(80, 99)

        return AimTrajectory(
            strikerPos = striker,
            coinPos = coin,
            targetPocket = bestPocket,
            pocketName = bestPocketName,
            directStrikeLine = directStrikeLine,
            coinToPocketLine = coinToPocketLine,
            strikerReboundLine = striker3Cushion,
            coinReboundLine = coin3Cushion,
            striker3CushionPoints = striker3Cushion,
            coin3CushionPoints = coin3Cushion,
            angleDegrees = normalizedAngle,
            isPocketLocked = isLocked,
            lockScorePercent = lockScore
        )
    }

    /**
     * Calculates multi-cushion bank rebound rays (up to [maxCushions] bounces)
     * against the 4 rectangular cushion walls of the Carrom board.
     */
    fun calculateMultiCushionRebound(
        start: PointF,
        direction: PointF,
        boardWidth: Float,
        boardHeight: Float,
        maxCushions: Int = 3,
        margin: Float = 25f
    ): List<PointF> {
        val points = mutableListOf<PointF>()
        points.add(PointF(start.x, start.y))

        val length = hypot(direction.x, direction.y)
        if (length < 0.001f) return points

        var currentStart = PointF(start.x, start.y)
        var dirX = direction.x / length
        var dirY = direction.y / length

        val minX = margin
        val maxX = boardWidth - margin
        val minY = margin
        val maxY = boardHeight - margin

        for (bounce in 0 until maxCushions) {
            var tMin = Float.MAX_VALUE
            var hitWall = -1 // 0=left, 1=right, 2=top, 3=bottom
            var hitX = 0f
            var hitY = 0f

            // Check X walls
            if (dirX > 0.0001f) {
                val t = (maxX - currentStart.x) / dirX
                if (t > 0.001f && t < tMin) {
                    tMin = t
                    hitWall = 1 // Right wall
                    hitX = maxX
                    hitY = currentStart.y + dirY * t
                }
            } else if (dirX < -0.0001f) {
                val t = (minX - currentStart.x) / dirX
                if (t > 0.001f && t < tMin) {
                    tMin = t
                    hitWall = 0 // Left wall
                    hitX = minX
                    hitY = currentStart.y + dirY * t
                }
            }

            // Check Y walls
            if (dirY > 0.0001f) {
                val t = (maxY - currentStart.y) / dirY
                if (t > 0.001f && t < tMin) {
                    tMin = t
                    hitWall = 3 // Bottom wall
                    hitX = currentStart.x + dirX * t
                    hitY = maxY
                }
            } else if (dirY < -0.0001f) {
                val t = (minY - currentStart.y) / dirY
                if (t > 0.001f && t < tMin) {
                    tMin = t
                    hitWall = 2 // Top wall
                    hitX = currentStart.x + dirX * t
                    hitY = minY
                }
            }

            if (tMin < Float.MAX_VALUE && hitWall != -1) {
                // Clamp hit point within boundaries
                val hitPoint = PointF(hitX.coerceIn(minX, maxX), hitY.coerceIn(minY, maxY))
                points.add(hitPoint)

                // Reflect direction according to wall
                when (hitWall) {
                    0, 1 -> dirX = -dirX // Left or Right wall reflection
                    2, 3 -> dirY = -dirY // Top or Bottom wall reflection
                }

                currentStart = hitPoint
            } else {
                break
            }
        }

        // Add a forward residual guide ray along the final reflection angle
        if (points.size > 1) {
            val lastPoint = points.last()
            val finalSegmentLength = 180f
            val endPoint = PointF(
                (lastPoint.x + dirX * finalSegmentLength).coerceIn(minX, maxX),
                (lastPoint.y + dirY * finalSegmentLength).coerceIn(minY, maxY)
            )
            points.add(endPoint)
        }

        return points
    }
}
