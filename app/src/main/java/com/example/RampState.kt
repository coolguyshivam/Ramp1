package com.example

import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Predefined vehicle profiles to test ramp compatibility.
 */
data class VehicleProfile(
    val name: String,
    val description: String,
    val wheelbase: Float,          // in feet
    val groundClearance: Float,    // in inches
    val frontOverhang: Float,      // in feet
    val rearOverhang: Float,       // in feet
    val frontBumperHeight: Float,  // in inches
    val rearBumperHeight: Float,   // in inches
    val powerDescription: String,
    val torqueClimbCapability: String,
    val overallHeight: Float       // in feet
) {
    companion object {
        val Hatchback = VehicleProfile(
            name = "Small Car / Hatchback",
            description = "E.g., Suzuki Alto, Hyundai i10, low-power FWD cars with low ground clearance.",
            wheelbase = 8.0f,          // 2440 mm
            groundClearance = 5.9f,    // 150 mm
            frontOverhang = 2.2f,      // 670 mm
            rearOverhang = 2.0f,       // 610 mm
            frontBumperHeight = 7.0f,  // 178 mm
            rearBumperHeight = 7.5f,   // 190 mm
            powerDescription = "Low Power (0.8L - 1.2L naturally aspirated engine)",
            torqueClimbCapability = "Struggles starting on slopes > 15% (1:6.7); wet FWD slipping risk.",
            overallHeight = 4.8f       // in feet
        )

        val Sedan = VehicleProfile(
            name = "Sedan / Midsize",
            description = "E.g., Honda Civic, Toyota Corolla. Moderate wheelbase and low-medium clearance.",
            wheelbase = 8.8f,          // 2680 mm
            groundClearance = 6.3f,    // 160 mm
            frontOverhang = 2.8f,      // 850 mm
            rearOverhang = 3.1f,       // 945 mm
            frontBumperHeight = 8.0f,  // 203 mm
            rearBumperHeight = 8.5f,   // 216 mm
            powerDescription = "Medium Power (1.5L - 2.0L naturally aspirated)",
            torqueClimbCapability = "Manageable up to 20% (1:5), FWD wheel-spin on wet slopes.",
            overallHeight = 4.7f       // in feet
        )

        val SUV = VehicleProfile(
            name = "SUV / Crossover",
            description = "E.g., Toyota RAV4, Honda CR-V. High clearance, short overhang ratios.",
            wheelbase = 9.2f,          // 2800 mm
            groundClearance = 8.3f,    // 210 mm
            frontOverhang = 2.5f,      // 760 mm
            rearOverhang = 2.6f,       // 790 mm
            frontBumperHeight = 11.0f, // 280 mm
            rearBumperHeight = 11.5f,  // 290 mm
            powerDescription = "High Power (2.0L+ or Turbo, often AWD/RWD)",
            torqueClimbCapability = "Comfortable climbing steep ramps up to 25% (1:4).",
            overallHeight = 5.5f       // in feet
        )

        val Custom = VehicleProfile(
            name = "Custom Vehicle",
            description = "Manually adjust dimensions below to test your own vehicle.",
            wheelbase = 8.5f,
            groundClearance = 6.0f,
            frontOverhang = 2.5f,
            rearOverhang = 2.5f,
            frontBumperHeight = 8.0f,
            rearBumperHeight = 8.5f,
            powerDescription = "Custom Power Specifications",
            torqueClimbCapability = "Depends on vehicle engine and drive layout.",
            overallHeight = 5.0f       // in feet
        )

        val ScooterWithRider = VehicleProfile(
            name = "Scooter with Rider",
            description = "E.g., Honda Activa, Vespa. Short wheelbase, high overhead clearance needed for the rider.",
            wheelbase = 4.1f,          // 1250 mm
            groundClearance = 5.7f,    // 145 mm
            frontOverhang = 0.8f,      // short front
            rearOverhang = 1.0f,       // short rear
            frontBumperHeight = 10.0f, // 254 mm
            rearBumperHeight = 10.0f,  // 254 mm
            powerDescription = "Very Low Power (110cc - 150cc scooter automatic transmission)",
            torqueClimbCapability = "Can climb up to 18-20% slope, CVT belt slipping risk if heavily loaded.",
            overallHeight = 6.0f       // 6.0 feet tall including rider sitting on it
        )

        val PRESETS = listOf(Hatchback, Sedan, SUV, ScooterWithRider, Custom)
    }
}

/**
 * Represents a single segment in a custom multi-segment ramp profile.
 */
data class RampSegment(
    val id: String,
    val name: String,
    val length: Float,       // length in feet
    val slopeRatio: Float    // rise/run, positive is upward, negative is downward, zero is flat
)

/**
 * Technical configurations for the ramp design.
 */
data class RampConfig(
    // 1. Upward Segment (for Flood Barrier)
    val gutterWidth: Float = 2.0f,         // Gutter covered by slab (feet)
    val upwardRampLength: Float = 2.0f,    // Next segment of upward slope (feet)
    val upwardSlopeRatioDenominator: Float = 5.0f, // 1:5 slope -> 5f (20%)
    
    // 2. Downward Segment
    val downwardSlopeRatioDenominator: Float = 8.0f, // 1:8 slope -> 8f (12.5%)
    val basementTopLevel: Float = 5.0f,    // Height of basement top ceiling above road level (feet)
    val requiredClearHeight: Float = 7.5f, // Required clearance opening height (feet)
    
    // 3. Physical Parameters for Materials
    val rampWidth: Float = 14.0f,          // Entrance / ramp width (feet)
    val slabThicknessInches: Float = 6.0f, // Concrete slab thickness (inches)
    val subbaseThicknessInches: Float = 4.0f, // Gravel/Sand sub-base thickness (inches)
    val concreteMixRatio: String = "M20 (1:1.5:3)", // Concrete grade
    val steelDensityKgPerM3: Float = 80.0f, // Steel density per cubic meter of concrete
    
    // 4. Transition Curves (Easements)
    val applyTransition: Boolean = true,
    val transitionStartLength: Float = 3.0f,  // Transition at start (feet)
    val transitionCrestLength: Float = 4.0f,  // Transition at crest (feet)
    val transitionBottomLength: Float = 4.0f,  // Transition at bottom (feet)

    // 5. Basement Open Space Length (before covered garage ceiling starts)
    val basementOpenSpaceLength: Float = 10.0f,
    val basementEntranceX: Float = 14.0f, // Distance from road where covered ceiling starts (feet)
    val basementEndX: Float = 30.0f,      // Distance from road where covered ceiling ends (feet)

    // 6. Custom Multi-Segment Ramp Support
    val useCustomSegments: Boolean = false,
    val segments: List<RampSegment> = listOf(
        RampSegment("1", "Gutter Slab", 2.0f, 0.0f),
        RampSegment("2", "Upward Rise (1:5)", 2.0f, 0.20f),     // 20% upward slope
        RampSegment("3", "Crest Flat", 1.0f, 0.0f),            // 1ft flat at peak
        RampSegment("4", "Downward Ramp (1:8)", 16.0f, -0.125f) // 12.5% downward slope
    )
) {
    val upwardSlopeRatio: Float get() = 1.0f / upwardSlopeRatioDenominator
    val downwardSlopeRatio: Float get() = 1.0f / downwardSlopeRatioDenominator
}

/**
 * Results of the scraping simulation at a single car position.
 */
data class ScrapingResult(
    val carX: Float,                  // Front wheel horizontal position
    val frontWheelY: Float,
    val rearWheelY: Float,
    val chassisSlope: Float,
    val chassisAngleRad: Float,
    
    val underbodyScrapeX: Float?,      // Horizontal position where underbody scrapes, if any
    val underbodyScrapeAmount: Float, // Max underbody penetration (feet) (positive means scraping)
    val frontBumperScrape: Boolean,
    val frontBumperScrapeAmount: Float,
    val rearBumperScrape: Boolean,
    val rearBumperScrapeAmount: Float,
    val roofScrape: Boolean,
    val roofScrapeAmount: Float,
    
    // Point-by-point details for drawing
    val frontWheelX: Float,
    val rearWheelX: Float,
    val frontBumperX: Float,
    val frontBumperY: Float,
    val rearBumperX: Float,
    val rearBumperY: Float,
    val chassisBottomYAtFrontWheel: Float,
    val chassisBottomYAtRearWheel: Float,
    
    // Points along the underbody to check for scraping
    val underbodyPoints: List<UnderbodyPointStatus>
)

data class UnderbodyPointStatus(
    val x: Float,
    val carBottomY: Float,
    val rampY: Float,
    val isScraping: Boolean
)

/**
 * Complete set of calculated reports for the ramp.
 */
data class RampReport(
    val config: RampConfig,
    
    // Geometric values
    val crestHeight: Float,           // Elevation of peak above road (feet)
    val basementFloorLevel: Float,    // Floor elevation relative to road (feet)
    val downwardVerticalDrop: Float,  // Total downward drop from peak (feet)
    val downwardHorizontalRun: Float, // Horizontal run of downward ramp (feet)
    val totalHorizontalLength: Float, // Total length from start to bottom (feet)
    
    // Surface lengths
    val upwardSurfaceLength: Float,
    val downwardSurfaceLength: Float,
    val totalSurfaceLength: Float,
    
    // Material Estimations
    val concreteVolumeCFT: Float,
    val concreteVolumeCum: Float,
    val subbaseVolumeCFT: Float,
    val subbaseVolumeCum: Float,
    
    // Material Proportions
    val cementBags: Int,
    val sandCFT: Float,
    val aggregateCFT: Float,
    val steelWeightKg: Float,
    
    // Hazards/Hurdles based on calculations
    val breakoverAngleDegrees: Float,
    val upwardSlopePercent: Float,
    val downwardSlopePercent: Float,
    val isCrestSlopeChangeSevere: Boolean // true if slope change is > 20%
)

object RampCalculator {

    /**
     * Get the ramp elevation (y) at a horizontal distance (x) from the start.
     */
    fun getRampHeight(x: Float, config: RampConfig): Float {
        if (config.useCustomSegments) {
            if (x <= 0f) return 0f
            var currentX = 0f
            var currentY = 0f
            for (segment in config.segments) {
                if (x <= currentX + segment.length) {
                    return currentY + (x - currentX) * segment.slopeRatio
                }
                currentY += segment.length * segment.slopeRatio
                currentX += segment.length
            }
            return currentY
        }

        val L_up = config.gutterWidth + config.upwardRampLength
        val S_up = config.upwardSlopeRatio
        val S_down = config.downwardSlopeRatio
        val y_crest = L_up * S_up
        val y_floor = config.basementTopLevel - config.requiredClearHeight
        val delta_y_down = y_crest - y_floor
        val L_down = delta_y_down / S_down
        val L_total = L_up + L_down

        if (!config.applyTransition) {
            return when {
                x < 0f -> 0f
                x <= L_up -> x * S_up
                x <= L_total -> y_crest - (x - L_up) * S_down
                else -> y_floor
            }
        } else {
            val T_start = config.transitionStartLength
            val T_crest = config.transitionCrestLength
            val T_bottom = config.transitionBottomLength

            val x_s1 = -T_start / 2f
            val x_s2 = T_start / 2f

            val x_c1 = L_up - T_crest / 2f
            val x_c2 = L_up + T_crest / 2f

            val x_b1 = L_total - T_bottom / 2f
            val x_b2 = L_total + T_bottom / 2f

            return when {
                x < x_s1 -> 0f
                x <= x_s2 -> {
                    val u = x - x_s1
                    (S_up / (2f * T_start)) * u * u
                }
                x < x_c1 -> {
                    x * S_up
                }
                x <= x_c2 -> {
                    val u = x - x_c1
                    val y_c1 = x_c1 * S_up
                    y_c1 + S_up * u - ((S_up + S_down) / (2f * T_crest)) * u * u
                }
                x < x_b1 -> {
                    val u_end = T_crest
                    val y_c1 = x_c1 * S_up
                    val y_c2 = y_c1 + S_up * u_end - ((S_up + S_down) / (2f * T_crest)) * u_end * u_end
                    y_c2 - (x - x_c2) * S_down
                }
                x <= x_b2 -> {
                    val u = x - x_b1
                    val u_end_c = T_crest
                    val y_c1 = x_c1 * S_up
                    val y_c2 = y_c1 + S_up * u_end_c - ((S_up + S_down) / (2f * T_crest)) * u_end_c * u_end_c
                    val y_b1 = y_c2 - (x_b1 - x_c2) * S_down
                    y_b1 - S_down * u + (S_down / (2f * T_bottom)) * u * u
                }
                else -> {
                    val u_end_c = T_crest
                    val y_c1 = x_c1 * S_up
                    val y_c2 = y_c1 + S_up * u_end_c - ((S_up + S_down) / (2f * T_crest)) * u_end_c * u_end_c
                    val y_b1 = y_c2 - (x_b1 - x_c2) * S_down
                    val y_b2 = y_b1 - S_down * T_bottom + (S_down / (2f * T_bottom)) * T_bottom * T_bottom
                    y_b2
                }
            }
        }
    }

    /**
     * Compute full report data for a given configuration.
     */
    fun generateReport(config: RampConfig): RampReport {
        val crestHeight: Float
        val basementFloorLevel: Float
        val downwardVerticalDrop: Float
        val downwardHorizontalRun: Float
        val totalHorizontalLength: Float
        val upwardSurfaceLength: Float
        val downwardSurfaceLength: Float
        val totalSurfaceLength: Float
        val upwardSlopePercent: Float
        val downwardSlopePercent: Float
        val breakoverAngleDegrees: Float
        val isCrestSlopeChangeSevere: Boolean

        if (config.useCustomSegments) {
            var maxElev = 0f
            var currentY = 0f
            var totalLength = 0f
            var totalSurface = 0f
            var upSurface = 0f
            var downSurface = 0f
            var maxSlopeDiff = 0f

            for (i in config.segments.indices) {
                val seg = config.segments[i]
                val startY = currentY
                val endY = currentY + seg.length * seg.slopeRatio
                maxElev = maxOf(maxElev, startY, endY)
                totalLength += seg.length
                
                val segSurf = seg.length * sqrt(1f + seg.slopeRatio * seg.slopeRatio)
                totalSurface += segSurf
                if (seg.slopeRatio >= 0f) {
                    upSurface += segSurf
                } else {
                    downSurface += segSurf
                }

                currentY = endY

                // Check angle diff with next segment
                if (i < config.segments.size - 1) {
                    val nextSeg = config.segments[i + 1]
                    val angleDiff = Math.abs(atan2(seg.slopeRatio, 1f) - atan2(nextSeg.slopeRatio, 1f))
                    maxSlopeDiff = maxOf(maxSlopeDiff, angleDiff)
                }
            }

            crestHeight = maxElev
            basementFloorLevel = currentY
            downwardVerticalDrop = crestHeight - basementFloorLevel
            downwardHorizontalRun = totalLength
            totalHorizontalLength = totalLength
            upwardSurfaceLength = upSurface
            downwardSurfaceLength = downSurface
            totalSurfaceLength = totalSurface

            // Approximate slopes for reporting from segment maxes
            val maxUpSlope = config.segments.filter { it.slopeRatio > 0f }.maxOfOrNull { it.slopeRatio } ?: 0f
            val maxDownSlope = config.segments.filter { it.slopeRatio < 0f }.minOfOrNull { it.slopeRatio } ?: 0f
            upwardSlopePercent = maxUpSlope * 100f
            downwardSlopePercent = -maxDownSlope * 100f

            breakoverAngleDegrees = maxSlopeDiff * (180f / Math.PI.toFloat())
            isCrestSlopeChangeSevere = breakoverAngleDegrees > 11.5f
        } else {
            val L_up = config.gutterWidth + config.upwardRampLength
            val S_up = config.upwardSlopeRatio
            val S_down = config.downwardSlopeRatio
            
            crestHeight = L_up * S_up
            basementFloorLevel = config.basementTopLevel - config.requiredClearHeight
            downwardVerticalDrop = crestHeight - basementFloorLevel
            downwardHorizontalRun = downwardVerticalDrop / S_down
            totalHorizontalLength = L_up + downwardHorizontalRun
            
            upwardSurfaceLength = L_up * sqrt(1f + S_up * S_up)
            downwardSurfaceLength = downwardHorizontalRun * sqrt(1f + S_down * S_down)
            totalSurfaceLength = upwardSurfaceLength + downwardSurfaceLength
            
            upwardSlopePercent = S_up * 100f
            downwardSlopePercent = S_down * 100f
            
            val angleUpRad = atan2(S_up, 1f)
            val angleDownRad = atan2(S_down, 1f)
            breakoverAngleDegrees = (angleUpRad + angleDownRad) * (180f / Math.PI.toFloat())
            
            isCrestSlopeChangeSevere = (S_up + S_down) > 0.20f // Total change in slope greater than 20%
        }
        
        // Concrete Volume (CFT)
        val slabThickFt = config.slabThicknessInches / 12f
        val concreteVolumeCFT = totalSurfaceLength * config.rampWidth * slabThickFt
        val concreteVolumeCum = concreteVolumeCFT / 35.3147f
        
        // Sub-base Volume (CFT)
        val subbaseThickFt = config.subbaseThicknessInches / 12f
        val subbaseVolumeCFT = totalSurfaceLength * config.rampWidth * subbaseThickFt
        val subbaseVolumeCum = subbaseVolumeCFT / 35.3147f
        
        // Material breakdown (using standard dry-mix conversion multiplier 1.54)
        // Ratio parts: M20 -> 1:1.5:3 -> sum = 5.5. Or custom based on selected mix
        val mixSum: Float
        val cementPart: Float
        val sandPart: Float
        val aggregatePart: Float
        
        when (config.concreteMixRatio) {
            "M15 (1:2:4)" -> {
                mixSum = 7.0f; cementPart = 1f; sandPart = 2f; aggregatePart = 4f
            }
            "M25 (1:1:2)" -> {
                mixSum = 4.0f; cementPart = 1f; sandPart = 1f; aggregatePart = 2f
            }
            else -> { // M20 (1:1.5:3) default
                mixSum = 5.5f; cementPart = 1f; sandPart = 1.5f; aggregatePart = 3f
            }
        }
        
        val dryVolumeCFT = concreteVolumeCFT * 1.54f
        val cementVolumeCFT = (cementPart / mixSum) * dryVolumeCFT
        val cementBags = Math.ceil(cementVolumeCFT / 1.25).toInt()
        
        val sandCFT = (sandPart / mixSum) * dryVolumeCFT
        val aggregateCFT = (aggregatePart / mixSum) * dryVolumeCFT
        val steelWeightKg = concreteVolumeCum * config.steelDensityKgPerM3
        
        return RampReport(
            config = config,
            crestHeight = crestHeight,
            basementFloorLevel = basementFloorLevel,
            downwardVerticalDrop = downwardVerticalDrop,
            downwardHorizontalRun = downwardHorizontalRun,
            totalHorizontalLength = totalHorizontalLength,
            upwardSurfaceLength = upwardSurfaceLength,
            downwardSurfaceLength = downwardSurfaceLength,
            totalSurfaceLength = totalSurfaceLength,
            concreteVolumeCFT = concreteVolumeCFT,
            concreteVolumeCum = concreteVolumeCum,
            subbaseVolumeCFT = subbaseVolumeCFT,
            subbaseVolumeCum = subbaseVolumeCum,
            cementBags = cementBags,
            sandCFT = sandCFT,
            aggregateCFT = aggregateCFT,
            steelWeightKg = steelWeightKg,
            breakoverAngleDegrees = breakoverAngleDegrees,
            upwardSlopePercent = upwardSlopePercent,
            downwardSlopePercent = downwardSlopePercent,
            isCrestSlopeChangeSevere = isCrestSlopeChangeSevere
        )
    }

    /**
     * Simulates the clearance profile of a vehicle at a given front wheel position [carX].
     */
    fun simulateVehicle(carX: Float, config: RampConfig, vehicle: VehicleProfile): ScrapingResult {
        val WB = vehicle.wheelbase
        val GC = vehicle.groundClearance / 12f // convert inches to feet
        val FOH = vehicle.frontOverhang
        val ROH = vehicle.rearOverhang
        val FBH = vehicle.frontBumperHeight / 12f
        val RBH = vehicle.rearBumperHeight / 12f
        val OH = vehicle.overallHeight // overall height in feet
        
        val frontWheelX = carX
        val rearWheelX = carX - WB
        
        val frontWheelY = getRampHeight(frontWheelX, config)
        val rearWheelY = getRampHeight(rearWheelX, config)
        
        val dx = frontWheelX - rearWheelX
        val dy = frontWheelY - rearWheelY
        val chassisSlope = dy / dx
        val chassisAngleRad = atan2(dy, dx)
        
        // Bumpers coordinate relative to chassis line
        // Front bumper is FOH forward along chassis slope, plus bumper height offset perpendicular to chassis
        val cosTheta = cos(chassisAngleRad)
        val sinTheta = sin(chassisAngleRad)
        
        val frontBumperX = frontWheelX + FOH * cosTheta
        val frontBumperY_chassis = frontWheelY + FOH * sinTheta
        val frontBumperX_actual = frontBumperX - FBH * sinTheta // offset outward/inward if angled
        val frontBumperY_actual = frontBumperY_chassis + FBH * cosTheta
        
        val rearBumperX = rearWheelX - ROH * cosTheta
        val rearBumperY_chassis = rearWheelY - ROH * sinTheta
        val rearBumperX_actual = rearBumperX - RBH * sinTheta
        val rearBumperY_actual = rearBumperY_chassis + RBH * cosTheta
        
        // Ramp heights at bumpers
        val rampYAtFrontBumper = getRampHeight(frontBumperX, config)
        val rampYAtRearBumper = getRampHeight(rearBumperX, config)
        
        val frontBumperScrapeAmount = rampYAtFrontBumper - frontBumperY_actual
        val frontBumperScrape = frontBumperScrapeAmount > 0f
        
        val rearBumperScrapeAmount = rampYAtRearBumper - rearBumperY_actual
        val rearBumperScrape = rearBumperScrapeAmount > 0f
        
        // Compute where the covered basement opening roof begins
        val L_up = config.gutterWidth + config.upwardRampLength
        val S_up = config.upwardSlopeRatio
        val S_down = config.downwardSlopeRatio
        val y_crest = L_up * S_up
        val y_floor = config.basementTopLevel - config.requiredClearHeight
        val delta_y_down = y_crest - y_floor
        val L_down = delta_y_down / S_down
        val L_total = L_up + L_down
        val entranceX = config.basementEntranceX

        // Chassis underbody check
        // Check 20 equidistant points between front wheel and rear wheel
        val underbodyPoints = mutableListOf<UnderbodyPointStatus>()
        var maxUnderbodyScrapeAmount = -999f
        var maxScrapeX: Float? = null
        
        var maxRoofScrapeAmount = 0f
        var roofScrape = false

        for (i in 0..20) {
            val ratio = i / 20f
            val ptX = rearWheelX + ratio * WB
            val ptYChassis = rearWheelY + ratio * dy
            // Underbody base height (shifted up by ground clearance GC)
            val ptYUnderbody = ptYChassis + GC * cosTheta
            val ptRampY = getRampHeight(ptX, config)
            
            val scrapeAmount = ptRampY - ptYUnderbody
            val isScraping = scrapeAmount > 0.005f // small threshold for precision
            
            underbodyPoints.add(
                UnderbodyPointStatus(
                    x = ptX,
                    carBottomY = ptYUnderbody,
                    rampY = ptRampY,
                    isScraping = isScraping
                )
            )
            
            if (scrapeAmount > maxUnderbodyScrapeAmount) {
                maxUnderbodyScrapeAmount = scrapeAmount
                if (isScraping) {
                    maxScrapeX = ptX
                }
            }

            // Check roof clearance relative to covered basement ceiling (between entrance and end point)
            val ptYRoof = ptYChassis + OH * cosTheta
            if (ptX >= config.basementEntranceX && ptX <= config.basementEndX) {
                val roofScrapeVal = ptYRoof - config.basementTopLevel
                if (roofScrapeVal > 0f) {
                    roofScrape = true
                    if (roofScrapeVal > maxRoofScrapeAmount) {
                        maxRoofScrapeAmount = roofScrapeVal
                    }
                }
            }
        }
        
        return ScrapingResult(
            carX = carX,
            frontWheelY = frontWheelY,
            rearWheelY = rearWheelY,
            chassisSlope = chassisSlope,
            chassisAngleRad = chassisAngleRad,
            underbodyScrapeX = maxScrapeX,
            underbodyScrapeAmount = if (maxUnderbodyScrapeAmount > 0f) maxUnderbodyScrapeAmount else 0f,
            frontBumperScrape = frontBumperScrape,
            frontBumperScrapeAmount = if (frontBumperScrapeAmount > 0f) frontBumperScrapeAmount else 0f,
            rearBumperScrape = rearBumperScrape,
            rearBumperScrapeAmount = if (rearBumperScrapeAmount > 0f) rearBumperScrapeAmount else 0f,
            roofScrape = roofScrape,
            roofScrapeAmount = if (maxRoofScrapeAmount > 0f) maxRoofScrapeAmount else 0f,
            frontWheelX = frontWheelX,
            rearWheelX = rearWheelX,
            frontBumperX = frontBumperX,
            frontBumperY = frontBumperY_actual,
            rearBumperX = rearBumperX,
            rearBumperY = rearBumperY_actual,
            chassisBottomYAtFrontWheel = frontWheelY + GC * cosTheta,
            chassisBottomYAtRearWheel = rearWheelY + GC * cosTheta,
            underbodyPoints = underbodyPoints
        )
    }

    /**
     * Generates a list of probable safe, scrape-free ramp configurations
     * for a given clear height requirement across small cars, SUVs, and scooters.
     */
    fun generateSafeDesigns(config: RampConfig): List<SafeDesignOption> {
        val vehicles = listOf(
            VehicleProfile.Hatchback,
            VehicleProfile.SUV,
            VehicleProfile.ScooterWithRider
        )
        
        val options = mutableListOf<SafeDesignOption>()
        
        // Let's test realistic combinations of parameters
        val downwardSlopes = listOf(7f, 8f, 9f, 10f, 11f, 12f)
        val upwardSlopes = listOf(5f, 6f)
        
        val crestX = config.gutterWidth + config.upwardRampLength
        val minEntranceX = Math.ceil(crestX + 2.0).toFloat()
        val entrancePositions = listOf(minEntranceX, minEntranceX + 3.0f, minEntranceX + 6.0f, minEntranceX + 9.0f, minEntranceX + 12.0f)
        
        var idCounter = 1
        for (upSlopeDen in upwardSlopes) {
            for (downSlopeDen in downwardSlopes) {
                for (entranceX in entrancePositions) {
                    val testConfig = config.copy(
                        upwardSlopeRatioDenominator = upSlopeDen,
                        downwardSlopeRatioDenominator = downSlopeDen,
                        basementEntranceX = entranceX,
                        basementEndX = maxOf(config.basementEndX, entranceX + 15f)
                    )
                    
                    val report = generateReport(testConfig)
                    val totalLength = report.totalHorizontalLength
                    
                    var allVehiclesSafe = true
                    var minUnderbodyGC = 999f
                    var minHeadroom = 999f
                    
                    val startX = -4.0f
                    val endX = totalLength + 6.0f
                    val step = 0.5f
                    var x = startX
                    while (x <= endX) {
                        val floorY = getRampHeight(x, testConfig)
                        if (x in testConfig.basementEntranceX..testConfig.basementEndX) {
                            val headroom = testConfig.basementTopLevel - floorY
                            if (headroom < testConfig.requiredClearHeight) {
                                allVehiclesSafe = false
                            }
                            minHeadroom = minOf(minHeadroom, headroom)
                        }
                        
                        for (vehicle in vehicles) {
                            val sim = simulateVehicle(x, testConfig, vehicle)
                            if (sim.underbodyScrapeAmount > 0f || sim.frontBumperScrape || sim.rearBumperScrape || sim.roofScrape) {
                                allVehiclesSafe = false
                            }
                            val underbodyClearance = (vehicle.groundClearance - sim.underbodyScrapeAmount * 12f)
                            minUnderbodyGC = minOf(minUnderbodyGC, underbodyClearance)
                        }
                        
                        x += step
                    }
                    
                    if (allVehiclesSafe) {
                        val name: String
                        val description: String
                        
                        when {
                            downSlopeDen == 8.0f && upSlopeDen == 5.0f -> {
                                name = "Standard Balanced Design"
                                description = "Uses classic 1:5 flood barrier & 1:8 downward slope. Highly standard, optimized for minimal horizontal space."
                            }
                            downSlopeDen >= 10.0f -> {
                                name = "Gentle Slope Comfort Profile"
                                description = "An ultra-comfortable 1:${downSlopeDen.toInt()} downward run. Great for luxury low-clearance sedans and scooters."
                            }
                            entranceX > minEntranceX + 4.0f -> {
                                name = "Extended Open Atrium Layout"
                                description = "Pushes the roof slab entrance back to ${entranceX.toInt()} ft. Provides abundance of natural light and ventilation."
                            }
                            else -> {
                                name = "Optimized Compact Profile"
                                description = "A 1:${downSlopeDen.toInt()} downward slope with roof starting at ${entranceX.toInt()} ft. Perfectly matches smaller layouts."
                            }
                        }
                        
                        options.add(
                            SafeDesignOption(
                                id = "opt_${idCounter++}",
                                name = name,
                                description = description,
                                upwardSlopeRatioDenominator = upSlopeDen,
                                downwardSlopeRatioDenominator = downSlopeDen,
                                basementEntranceX = entranceX,
                                totalHorizontalLength = totalLength,
                                maxCrestHeight = report.crestHeight,
                                isRecommended = (downSlopeDen == 8.0f || downSlopeDen == 10.0f) && (upSlopeDen == 5.0f)
                            )
                        )
                    }
                }
            }
        }
        
        return options.sortedWith(
            compareByDescending<SafeDesignOption> { it.isRecommended }
                .thenByDescending { it.downwardSlopeRatioDenominator }
                .thenBy { it.totalHorizontalLength }
        ).take(4)
    }
}

/**
 * Data class representing a recommended safe configuration found by the optimizer.
 */
data class SafeDesignOption(
    val id: String,
    val name: String,
    val description: String,
    val upwardSlopeRatioDenominator: Float,
    val downwardSlopeRatioDenominator: Float,
    val basementEntranceX: Float,
    val totalHorizontalLength: Float,
    val maxCrestHeight: Float,
    val isRecommended: Boolean
)
