package com.example

// Ramp Designer MainActivity - Handles UI and simulation components


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    RampDesignerScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RampDesignerScreen(modifier: Modifier = Modifier) {
    // 1. Core State
    var config by remember { mutableStateOf(RampConfig()) }
    var selectedVehiclePreset by remember { mutableStateOf(VehicleProfile.Hatchback) }
    
    // Sliders for Custom Vehicle, if selected
    var customWheelbase by remember { mutableFloatStateOf(VehicleProfile.Custom.wheelbase) }
    var customGroundClearance by remember { mutableFloatStateOf(VehicleProfile.Custom.groundClearance) }
    var customFrontOverhang by remember { mutableFloatStateOf(VehicleProfile.Custom.frontOverhang) }
    var customRearOverhang by remember { mutableFloatStateOf(VehicleProfile.Custom.rearOverhang) }
    var customFrontBumperHeight by remember { mutableFloatStateOf(VehicleProfile.Custom.frontBumperHeight) }
    var customRearBumperHeight by remember { mutableFloatStateOf(VehicleProfile.Custom.rearBumperHeight) }
    var customOverallHeight by remember { mutableFloatStateOf(VehicleProfile.Custom.overallHeight) }

    // Synchronize selected vehicle with customized dimensions
    val activeVehicle = remember(
        selectedVehiclePreset,
        customWheelbase,
        customGroundClearance,
        customFrontOverhang,
        customRearOverhang,
        customFrontBumperHeight,
        customRearBumperHeight,
        customOverallHeight
    ) {
        if (selectedVehiclePreset.name == "Custom Vehicle") {
            VehicleProfile.Custom.copy(
                wheelbase = customWheelbase,
                groundClearance = customGroundClearance,
                frontOverhang = customFrontOverhang,
                rearOverhang = customRearOverhang,
                frontBumperHeight = customFrontBumperHeight,
                rearBumperHeight = customRearBumperHeight,
                overallHeight = customOverallHeight
            )
        } else {
            selectedVehiclePreset
        }
    }

    // Report based on Configuration
    val report = remember(config) {
        RampCalculator.generateReport(config)
    }

    // Car position slider (horizontal coordinate of front wheel relative to the start)
    // Range covers some buffer on road (-8 ft) to past basement end point
    val maxScrollDist = maxOf(report.totalHorizontalLength, config.basementEndX) + 8f
    var carPositionX by remember(report.totalHorizontalLength, config.basementEndX) { mutableFloatStateOf(0f) }

    // Headroom inspector horizontal position (feet from road)
    var inspectorX by remember(config.basementEntranceX, config.basementEndX) { mutableFloatStateOf(config.basementEntranceX) }

    // Zoom and pan states for the interactive visual canvas
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffsetFeetX by remember { mutableFloatStateOf(0.0f) }
    var panOffsetFeetY by remember { mutableFloatStateOf(0.0f) }
    var focusMode by remember { mutableStateOf("Full") } // "Full", "Car", "Crest", "Entrance", "Manual"

    // Run scraping simulation
    val simulationResult = remember(carPositionX, config, activeVehicle) {
        RampCalculator.simulateVehicle(carPositionX, config, activeVehicle)
    }

    val hasAnyScrape = simulationResult.underbodyScrapeAmount > 0f ||
            simulationResult.frontBumperScrape ||
            simulationResult.rearBumperScrape ||
            simulationResult.roofScrape

    // Expandable settings states
    var showSlopeSettings by remember { mutableStateOf(true) }
    var showMaterialSettings by remember { mutableStateOf(false) }
    var activeDragHandle by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. HEADER SECTION ---
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📐", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Ramp Designer",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "Basement Parking Ramp Architect & Simulator",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
        }

        // --- 2. VEHICLE PRESETS PANEL ---
        item {
            Column {
                Text(
                    text = "Select Vehicle to Simulate Scrape Risks:",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4
                ) {
                    VehicleProfile.PRESETS.forEach { preset ->
                        val isSelected = selectedVehiclePreset.name == preset.name
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    selectedVehiclePreset = preset
                                    // Reset car position to start when changing preset
                                    carPositionX = 0f
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (preset.name) {
                                        "Small Car / Hatchback" -> "🚗 "
                                        "Sedan / Midsize" -> "🚘 "
                                        "SUV / Crossover" -> "🚙 "
                                        else -> "⚙️ "
                                    }
                                )
                                Text(
                                    text = preset.name.split(" / ").first(),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Vehicle Stats Display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Specs: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(activeVehicle.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            VehicleSpecBadge(label = "Wheelbase", value = "${activeVehicle.wheelbase} ft")
                            VehicleSpecBadge(label = "Clearance", value = "${activeVehicle.groundClearance}\"")
                            VehicleSpecBadge(label = "Bumper Height (F/R)", value = "${activeVehicle.frontBumperHeight}\" / ${activeVehicle.rearBumperHeight}\"")
                        }
                        
                        // Engine Climb & Traction details
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Traction",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Vehicle Climbing Power:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "${activeVehicle.powerDescription} - ${activeVehicle.torqueClimbCapability}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- CUSTOM VEHICLE SLIDERS (Only visible if Custom preset is active) ---
        if (selectedVehiclePreset.name == "Custom Vehicle") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Custom Vehicle Dimensions",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Wheelbase: ${String.format("%.1f", customWheelbase)} feet", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = customWheelbase,
                            onValueChange = { customWheelbase = it },
                            valueRange = 6.0f..12.0f,
                            modifier = Modifier.testTag("custom_wheelbase_slider")
                        )
                        
                        Text("Ground Clearance: ${String.format("%.1f", customGroundClearance)} inches", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = customGroundClearance,
                            onValueChange = { customGroundClearance = it },
                            valueRange = 4.0f..12.0f,
                            modifier = Modifier.testTag("custom_gc_slider")
                        )

                        Text("Overall Vehicle Height: ${String.format("%.1f", customOverallHeight)} feet", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = customOverallHeight,
                            onValueChange = { customOverallHeight = it },
                            valueRange = 3.5f..7.5f,
                            modifier = Modifier.testTag("custom_height_slider")
                        )

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Front Overhang: ${String.format("%.1f", customFrontOverhang)} ft", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = customFrontOverhang,
                                    onValueChange = { customFrontOverhang = it },
                                    valueRange = 1.0f..4.0f
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rear Overhang: ${String.format("%.1f", customRearOverhang)} ft", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = customRearOverhang,
                                    onValueChange = { customRearOverhang = it },
                                    valueRange = 1.0f..4.0f
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Front Bumper Height: ${String.format("%.1f", customFrontBumperHeight)}\"", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = customFrontBumperHeight,
                                    onValueChange = { customFrontBumperHeight = it },
                                    valueRange = 5.0f..15.0f
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rear Bumper Height: ${String.format("%.1f", customRearBumperHeight)}\"", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = customRearBumperHeight,
                                    onValueChange = { customRearBumperHeight = it },
                                    valueRange = 5.0f..15.0f
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 3. INTERACTIVE VISUAL CANVAS CARD ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Ramp Centerline Cross-Section & Clearance",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Drag slider below to roll the car. Red zones indicate scraping risks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulated drawing Canvas
                    val density = LocalDensity.current
                    val surfaceColor = MaterialTheme.colorScheme.surface
                    val textLabelColor = MaterialTheme.colorScheme.onSurface
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                            .pointerInput(config, report, carPositionX, inspectorX, zoomLevel, panOffsetFeetX, panOffsetFeetY, focusMode) {
                                detectTapGestures { offset ->
                                    val width = size.width.toFloat()
                                    val height = size.height.toFloat()
                                    
                                    val xMin = -6f
                                    val xMax = maxOf(report.totalHorizontalLength, config.basementEndX) + 8f
                                    val yMin = report.basementFloorLevel - 1.5f
                                    val yMax = maxOf(config.basementTopLevel, report.crestHeight) + 1.2f

                                    val totalXRange = xMax - xMin
                                    val totalYRange = yMax - yMin

                                    val paddingLeftRight = 40f
                                    val paddingTopBottom = 40f

                                    val viewCenterX = width / 2f
                                    val viewCenterY = height / 2f

                                    val scaleX = (width - 2f * paddingLeftRight) / totalXRange
                                    val scaleY = (height - 2f * paddingTopBottom) / totalYRange
                                    val baseScale = minOf(scaleX, scaleY)
                                    val scale = baseScale * zoomLevel

                                    val defaultCenterX = (xMin + xMax) / 2f
                                    val defaultCenterY = (yMin + yMax) / 2f

                                    val focusX = when (focusMode) {
                                        "Car" -> carPositionX
                                        "Crest" -> config.gutterWidth + config.upwardRampLength
                                        "Entrance" -> config.basementEntranceX
                                        else -> defaultCenterX + panOffsetFeetX
                                    }
                                    val focusY = when (focusMode) {
                                        "Car" -> 1.2f
                                        "Crest" -> report.crestHeight
                                        "Entrance" -> config.basementTopLevel - 1.0f
                                        else -> defaultCenterY + panOffsetFeetY
                                    }
                                    
                                    fun toCX(x: Float): Float = viewCenterX + (x - focusX) * scale
                                    fun toCY(y: Float): Float = viewCenterY - (y - focusY) * scale
                                    fun toFeetX(cx: Float): Float = focusX + (cx - viewCenterX) / scale

                                    // Let's check distance to other handles to avoid stealing clicks from handles
                                    val entranceCx = toCX(config.basementEntranceX)
                                    val entranceCy = toCY(config.basementTopLevel - 0.6f)
                                    val distToEntrance = Math.hypot((offset.x - entranceCx).toDouble(), (offset.y - entranceCy).toDouble())
                                    
                                    val endCx = toCX(config.basementEndX)
                                    val endCy = toCY(config.basementTopLevel - 0.6f)
                                    val distToBasementEnd = Math.hypot((offset.x - endCx).toDouble(), (offset.y - endCy).toDouble())

                                    val ceilingCx = toCX((config.basementEntranceX + config.basementEndX) / 2f)
                                    val ceilingCy = toCY(config.basementTopLevel - 0.3f)
                                    val distToCeiling = Math.hypot((offset.x - ceilingCx).toDouble(), (offset.y - ceilingCy).toDouble())

                                    val crestX = config.gutterWidth + config.upwardRampLength
                                    val crestCx = toCX(crestX)
                                    val crestCy = toCY(report.crestHeight)
                                    val distToCrest = Math.hypot((offset.x - crestCx).toDouble(), (offset.y - crestCy).toDouble())

                                    // If not close to any handles, move the inspectorX
                                    if (distToEntrance >= 45f && distToBasementEnd >= 45f && distToCeiling >= 45f && distToCrest >= 45f) {
                                        val tappedX = toFeetX(offset.x).coerceIn(0.0f, maxScrollDist)
                                        inspectorX = tappedX
                                    }
                                }
                            }
                            .pointerInput(config, report, carPositionX, inspectorX, zoomLevel, panOffsetFeetX, panOffsetFeetY, focusMode) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val width = size.width.toFloat()
                                        val height = size.height.toFloat()
                                        
                                        val xMin = -6f
                                        val xMax = maxOf(report.totalHorizontalLength, config.basementEndX) + 8f
                                        val yMin = report.basementFloorLevel - 1.5f
                                        val yMax = maxOf(config.basementTopLevel, report.crestHeight) + 1.2f

                                        val totalXRange = xMax - xMin
                                        val totalYRange = yMax - yMin

                                        val paddingLeftRight = 40f
                                        val paddingTopBottom = 40f

                                        val viewCenterX = width / 2f
                                        val viewCenterY = height / 2f

                                        val scaleX = (width - 2f * paddingLeftRight) / totalXRange
                                        val scaleY = (height - 2f * paddingTopBottom) / totalYRange
                                        val baseScale = minOf(scaleX, scaleY)
                                        val scale = baseScale * zoomLevel

                                        val defaultCenterX = (xMin + xMax) / 2f
                                        val defaultCenterY = (yMin + yMax) / 2f

                                        val focusX = when (focusMode) {
                                            "Car" -> carPositionX
                                            "Crest" -> config.gutterWidth + config.upwardRampLength
                                            "Entrance" -> config.basementEntranceX
                                            else -> defaultCenterX + panOffsetFeetX
                                        }
                                        val focusY = when (focusMode) {
                                            "Car" -> 1.2f
                                            "Crest" -> report.crestHeight
                                            "Entrance" -> config.basementTopLevel - 1.0f
                                            else -> defaultCenterY + panOffsetFeetY
                                        }
                                        
                                        fun toCX(x: Float): Float = viewCenterX + (x - focusX) * scale
                                        fun toCY(y: Float): Float = viewCenterY - (y - focusY) * scale
                                        fun toFeetX(cx: Float): Float = focusX + (cx - viewCenterX) / scale
                                        
                                        // Handle distances
                                        // 1. Car Position
                                        val carCx = toCX(carPositionX)
                                        val distToCar = Math.hypot((offset.x - carCx).toDouble(), (offset.y - toCY(0.5f)).toDouble())
                                        
                                        // 2. Basement Entrance X Handle (lip bottom corner)
                                        val entranceCx = toCX(config.basementEntranceX)
                                        val entranceCy = toCY(config.basementTopLevel - 0.6f)
                                        val distToEntrance = Math.hypot((offset.x - entranceCx).toDouble(), (offset.y - entranceCy).toDouble())
                                        
                                        // 3. Basement End X Handle (lip back corner)
                                        val endCx = toCX(config.basementEndX)
                                        val endCy = toCY(config.basementTopLevel - 0.6f)
                                        val distToBasementEnd = Math.hypot((offset.x - endCx).toDouble(), (offset.y - endCy).toDouble())

                                        // 4. Ceiling Height Handle (centered horizontally)
                                        val ceilingCx = toCX((config.basementEntranceX + config.basementEndX) / 2f)
                                        val ceilingCy = toCY(config.basementTopLevel - 0.3f)
                                        val distToCeiling = Math.hypot((offset.x - ceilingCx).toDouble(), (offset.y - ceilingCy).toDouble())
                                        
                                        // 5. Crest Height / Incline Handle
                                        val crestX = config.gutterWidth + config.upwardRampLength
                                        val crestCx = toCX(crestX)
                                        val crestCy = toCY(report.crestHeight)
                                        val distToCrest = Math.hypot((offset.x - crestCx).toDouble(), (offset.y - crestCy).toDouble())

                                        // 6. Headroom Inspector Handle
                                        val inspectorCx = toCX(inspectorX)
                                        val inspectorCy = toCY(RampCalculator.getRampHeight(inspectorX, config))
                                        val distToInspector = Math.hypot((offset.x - inspectorCx).toDouble(), (offset.y - inspectorCy).toDouble())
                                        
                                        activeDragHandle = when {
                                            distToEntrance < 45f -> "entrance"
                                            distToBasementEnd < 45f -> "basementEnd"
                                            distToCeiling < 45f -> "ceiling"
                                            distToInspector < 45f -> "inspector"
                                            distToCrest < 45f -> "crest"
                                            distToCar < 80f -> "car"
                                            else -> {
                                                if (zoomLevel > 1.0f) "pan" else "inspector"
                                            }
                                        }

                                        if (activeDragHandle == "inspector") {
                                            val tappedX = toFeetX(offset.x).coerceIn(0.0f, maxScrollDist)
                                            inspectorX = tappedX
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        val width = size.width.toFloat()
                                        val height = size.height.toFloat()
                                        
                                        val xMin = -6f
                                        val xMax = maxOf(report.totalHorizontalLength, config.basementEndX) + 8f
                                        val yMin = report.basementFloorLevel - 1.5f
                                        val yMax = maxOf(config.basementTopLevel, report.crestHeight) + 1.2f

                                        val totalXRange = xMax - xMin
                                        val totalYRange = yMax - yMin

                                        val paddingLeftRight = 40f
                                        val paddingTopBottom = 40f

                                        val viewCenterX = width / 2f
                                        val viewCenterY = height / 2f

                                        val scaleX = (width - 2f * paddingLeftRight) / totalXRange
                                        val scaleY = (height - 2f * paddingTopBottom) / totalYRange
                                        val baseScale = minOf(scaleX, scaleY)
                                        val scale = baseScale * zoomLevel
                                        
                                        val dragXFt = dragAmount.x / scale
                                        val dragYFt = -dragAmount.y / scale // Invert vertical direction
                                        
                                        when (activeDragHandle) {
                                            "pan" -> {
                                                focusMode = "Manual"
                                                panOffsetFeetX = (panOffsetFeetX - dragXFt).coerceIn(-30f, 30f)
                                                panOffsetFeetY = (panOffsetFeetY - dragYFt).coerceIn(-15f, 15f)
                                            }
                                            "car" -> {
                                                carPositionX = (carPositionX + dragXFt).coerceIn(-6f, maxScrollDist)
                                            }
                                            "entrance" -> {
                                                val newEntranceX = (config.basementEntranceX + dragXFt).coerceIn(4.0f, config.basementEndX - 2.0f)
                                                config = config.copy(basementEntranceX = newEntranceX)
                                            }
                                            "basementEnd" -> {
                                                val newEndX = (config.basementEndX + dragXFt).coerceIn(config.basementEntranceX + 2.0f, 60.0f)
                                                config = config.copy(basementEndX = newEndX)
                                            }
                                            "ceiling" -> {
                                                val newCeiling = (config.basementTopLevel + dragYFt).coerceIn(3.0f, 10.0f)
                                                config = config.copy(basementTopLevel = newCeiling)
                                            }
                                            "crest" -> {
                                                val newUpwardLength = (config.upwardRampLength + dragXFt).coerceIn(1.0f, 15.0f)
                                                config = config.copy(upwardRampLength = newUpwardLength)
                                            }
                                            "inspector" -> {
                                                inspectorX = (inspectorX + dragXFt).coerceIn(0.0f, maxScrollDist)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        activeDragHandle = null
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                            val width = size.width
                            val height = size.height

                            // Visual coordinate boundary
                            val xMin = -6f
                            val xMax = maxOf(report.totalHorizontalLength, config.basementEndX) + 8f
                            val yMin = report.basementFloorLevel - 1.5f
                            val yMax = maxOf(config.basementTopLevel, report.crestHeight) + 1.2f

                            val totalXRange = xMax - xMin
                            val totalYRange = yMax - yMin

                            val paddingLeftRight = 40f
                            val paddingTopBottom = 40f

                            // Center of the canvas area in pixels
                            val viewCenterX = width / 2f
                            val viewCenterY = height / 2f

                            // 1. Base scale fitted to screen
                            val scaleX = (width - 2f * paddingLeftRight) / totalXRange
                            val scaleY = (height - 2f * paddingTopBottom) / totalYRange
                            val baseScale = minOf(scaleX, scaleY)
                            
                            // 2. Active scale with zoom
                            val scale = baseScale * zoomLevel

                            // 3. Focal point centered in feet
                            val defaultCenterX = (xMin + xMax) / 2f
                            val defaultCenterY = (yMin + yMax) / 2f

                            val focusX = when (focusMode) {
                                "Car" -> carPositionX
                                "Crest" -> config.gutterWidth + config.upwardRampLength
                                "Entrance" -> config.basementEntranceX
                                else -> defaultCenterX + panOffsetFeetX
                            }
                            val focusY = when (focusMode) {
                                "Car" -> 1.2f
                                "Crest" -> report.crestHeight
                                "Entrance" -> config.basementTopLevel - 1.0f
                                else -> defaultCenterY + panOffsetFeetY
                            }

                            // 4. Coordinates projection functions
                            fun toCX(x: Float): Float = viewCenterX + (x - focusX) * scale
                            fun toCY(y: Float): Float = viewCenterY - (y - focusY) * scale

                            // 1. DRAW WATER LEVEL / ROAD LEVEL COORDINATES
                            // Horizontal ground baseline
                            drawLine(
                                color = Color.LightGray,
                                start = Offset(toCX(xMin), toCY(0f)),
                                end = Offset(toCX(xMax), toCY(0f)),
                                strokeWidth = 1f
                            )

                            // 2. DRAW THE CONCRETE RAMP SUB-STRUCTURE
                            val pointsCount = 100
                            val rampPath = Path()
                            val firstX = xMin
                            rampPath.moveTo(toCX(firstX), toCY(RampCalculator.getRampHeight(firstX, config)))
                            
                            for (i in 1..pointsCount) {
                                val frac = i.toFloat() / pointsCount
                                val pX = xMin + frac * (xMax - xMin)
                                val pY = RampCalculator.getRampHeight(pX, config)
                                rampPath.lineTo(toCX(pX), toCY(pY))
                            }
                            
                            // Draw concrete top surface
                            drawPath(
                                path = rampPath,
                                color = Color(0xFF64748B), // Concrete Slate Grey
                                style = Stroke(width = 5f)
                            )

                            // Under-concrete filling (Slab representation)
                            val filledConcretePath = Path()
                            filledConcretePath.moveTo(toCX(xMin), toCY(RampCalculator.getRampHeight(xMin, config)))
                            for (i in 1..pointsCount) {
                                val frac = i.toFloat() / pointsCount
                                val pX = xMin + frac * (xMax - xMin)
                                val pY = RampCalculator.getRampHeight(pX, config)
                                filledConcretePath.lineTo(toCX(pX), toCY(pY))
                            }
                            // Bottom depth of concrete (subtract slab thickness)
                            val slabThickFt = config.slabThicknessInches / 12f
                            for (i in pointsCount downTo 0) {
                                val frac = i.toFloat() / pointsCount
                                val pX = xMin + frac * (xMax - xMin)
                                val pY = RampCalculator.getRampHeight(pX, config) - slabThickFt
                                filledConcretePath.lineTo(toCX(pX), toCY(pY))
                            }
                            filledConcretePath.close()
                            drawPath(
                                path = filledConcretePath,
                                color = Color(0xFF94A3B8).copy(alpha = 0.5f) // Transparent concrete subbase
                            )

                            // 3. DRAW FRONT PROPERTY COVERED GUTTER (0 to 2 feet)
                            val gutterStartX = toCX(0f)
                            val gutterEndX = toCX(config.gutterWidth)
                            val gutterY = toCY(0f)
                            
                            // Draw covered gutter hatch box
                            drawRect(
                                color = Color(0xFF38BDF8).copy(alpha = 0.3f), // Water representation
                                topLeft = Offset(gutterStartX, gutterY),
                                size = Size(gutterEndX - gutterStartX, toCY(report.basementFloorLevel) - gutterY)
                            )
                            // Draw Gutter Cover Slab
                            drawLine(
                                color = Color(0xFF334155),
                                start = Offset(gutterStartX, toCY(RampCalculator.getRampHeight(0f, config))),
                                end = Offset(gutterEndX, toCY(RampCalculator.getRampHeight(config.gutterWidth, config))),
                                strokeWidth = 8f
                            )

                            // 4. DRAW BASEMENT CEILING AND OPENING
                            // Ceiling starts at basementEntranceX and ends at basementEndX
                            val entranceX = config.basementEntranceX
                            val ceilStartX = toCX(entranceX)
                            val ceilEndX = toCX(config.basementEndX)
                            val ceilYTop = toCY(config.basementTopLevel)
                            val ceilYBottom = toCY(config.basementTopLevel - 0.6f) // 7 inches slab
                            
                            // Basement ceiling slab representation
                            drawRect(
                                color = Color(0xFF475569),
                                topLeft = Offset(ceilStartX, ceilYTop),
                                size = Size(ceilEndX - ceilStartX, ceilYBottom - ceilYTop)
                            )

                            // Facade Wall going upwards representing the building exterior
                            drawRect(
                                color = Color(0xFF1E293B),
                                topLeft = Offset(ceilStartX, toCY(yMax)),
                                size = Size(14f * scale, ceilYTop - toCY(yMax))
                            )

                            // Hazard yellow/black warning stripes on the ceiling entrance lip
                            val lipHeight = ceilYBottom - ceilYTop
                            drawRect(
                                color = Color(0xFFFBBF24), // Warning yellow
                                topLeft = Offset(ceilStartX - 6f, ceilYTop),
                                size = Size(6f, lipHeight)
                            )
                            for (stripeY in 0..6) {
                                if (stripeY % 2 == 0) {
                                    val startStripeY = ceilYTop + (stripeY / 6f) * lipHeight
                                    val endStripeY = ceilYTop + ((stripeY + 1) / 6f) * lipHeight
                                    drawLine(
                                        color = Color.Black,
                                        start = Offset(ceilStartX - 6f, startStripeY),
                                        end = Offset(ceilStartX, endStripeY),
                                        strokeWidth = 2f
                                    )
                                }
                            }
                            
                            // Basement opening vertical wall guide (dashed line)
                            drawLine(
                                color = Color(0xFF0F172A),
                                start = Offset(ceilStartX, ceilYBottom),
                                end = Offset(ceilStartX, toCY(report.basementFloorLevel)),
                                strokeWidth = 3f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                            )

                            // 5. HIGHLIGHT KEY DIMENSIONS & LABELS
                            // Crest node
                            val crestX = config.gutterWidth + config.upwardRampLength
                            val crestY = report.crestHeight
                            drawCircle(
                                color = Color(0xFFF59E0B),
                                radius = 6f,
                                center = Offset(toCX(crestX), toCY(crestY))
                            )

                            // --- VISUALLY RICH MARKINGS FOR BASEMENT ENTRY AND HEIGHTS ---
                            // A. Vertical Opening Clear Height dimension line
                            val floorYAtEntrance = RampCalculator.getRampHeight(entranceX, config)
                            val entryClearance = config.basementTopLevel - floorYAtEntrance
                            val clearanceLineX = ceilStartX + 12f // Offset slightly inside the garage
                            
                            drawLine(
                                color = Color(0xFF10B981), // Emerald indicator line
                                start = Offset(clearanceLineX, toCY(floorYAtEntrance)),
                                end = Offset(clearanceLineX, ceilYBottom),
                                strokeWidth = 2.5f
                            )
                            drawCircle(color = Color(0xFF10B981), radius = 4f, center = Offset(clearanceLineX, toCY(floorYAtEntrance)))
                            drawCircle(color = Color(0xFF10B981), radius = 4f, center = Offset(clearanceLineX, ceilYBottom))

                            // B. Horizontal Open Setback dimension line from crest to entrance
                            val crestXCoord = config.gutterWidth + config.upwardRampLength
                            val crestCXCoord = toCX(crestXCoord)
                            val setbackLineY = toCY(maxOf(0f, report.crestHeight) + 0.5f)
                            val setbackVal = config.basementEntranceX - crestXCoord
                            if (setbackVal > 0.1f) {
                                drawLine(
                                    color = Color(0xFF3B82F6), // Blue dimension line
                                    start = Offset(crestCXCoord, setbackLineY),
                                    end = Offset(ceilStartX, setbackLineY),
                                    strokeWidth = 2f
                                )
                                drawCircle(color = Color(0xFF3B82F6), radius = 4f, center = Offset(crestCXCoord, setbackLineY))
                                drawCircle(color = Color(0xFF3B82F6), radius = 4f, center = Offset(ceilStartX, setbackLineY))
                            }

                            // C. Draw annotations directly onto Canvas
                            val paint = Paint().apply {
                                color = textLabelColor.toArgb()
                                textSize = 9.sp.toPx()
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                textAlign = Paint.Align.CENTER
                            }

                            // Horizontal Setback text
                            if (setbackVal > 0.1f) {
                                val setbackText = "Setback: ${String.format("%.1f", setbackVal)} ft"
                                val setbackWidth = paint.measureText(setbackText)
                                val setbackHeight = paint.textSize
                                val setbackBoxWidth = setbackWidth + 14f
                                val setbackBoxHeight = setbackHeight + 8f
                                val setbackBoxLeft = ((crestCXCoord + ceilStartX) / 2f) - setbackBoxWidth / 2f
                                val setbackBoxTop = setbackLineY - 6f - setbackBoxHeight / 2f - 4f
                                
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.95f),
                                    topLeft = Offset(setbackBoxLeft, setbackBoxTop),
                                    size = Size(setbackBoxWidth, setbackBoxHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                )
                                drawRoundRect(
                                    color = Color(0xFF3B82F6),
                                    topLeft = Offset(setbackBoxLeft, setbackBoxTop),
                                    size = Size(setbackBoxWidth, setbackBoxHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                                    style = Stroke(width = 1f)
                                )

                                paint.color = Color(0xFF1D4ED8).toArgb() // Blue text
                                paint.textAlign = Paint.Align.CENTER
                                drawContext.canvas.nativeCanvas.drawText(
                                    setbackText,
                                    (crestCXCoord + ceilStartX) / 2f,
                                    setbackBoxTop + setbackBoxHeight / 2f + setbackHeight / 2f - 1f,
                                    paint
                                )
                            }

                            // Vertical Clearance text
                            val clearanceText = "Clear: ${String.format("%.1f", entryClearance)} ft"
                            val clearanceWidth = paint.measureText(clearanceText)
                            val clearanceHeight = paint.textSize
                            val clearanceBoxWidth = clearanceWidth + 14f
                            val clearanceBoxHeight = clearanceHeight + 8f
                            val clearanceBoxLeft = clearanceLineX + 8f
                            val clearanceBoxTop = ((toCY(floorYAtEntrance) + ceilYBottom) / 2f) - clearanceBoxHeight / 2f
                            
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.95f),
                                topLeft = Offset(clearanceBoxLeft, clearanceBoxTop),
                                size = Size(clearanceBoxWidth, clearanceBoxHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                            )
                            drawRoundRect(
                                color = Color(0xFF10B981),
                                topLeft = Offset(clearanceBoxLeft, clearanceBoxTop),
                                size = Size(clearanceBoxWidth, clearanceBoxHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                                style = Stroke(width = 1f)
                             )

                            paint.color = Color(0xFF047857).toArgb() // Emerald text
                            paint.textAlign = Paint.Align.LEFT
                            drawContext.canvas.nativeCanvas.drawText(
                                clearanceText,
                                clearanceLineX + 15f,
                                clearanceBoxTop + clearanceBoxHeight / 2f + clearanceHeight / 2f - 1f,
                                paint
                            )

                            // Title texts for garage/portal
                            paint.color = textLabelColor.toArgb()
                            paint.textAlign = Paint.Align.CENTER
                            paint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                            drawContext.canvas.nativeCanvas.drawText(
                                "GARAGE ENTRANCE",
                                ceilStartX + 45f * scale,
                                ceilYTop - 25f,
                                paint
                            )
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            drawContext.canvas.nativeCanvas.drawText(
                                "Ceiling: +${String.format("%.1f", config.basementTopLevel)} ft",
                                ceilStartX + 45f * scale,
                                ceilYTop - 10f,
                                paint
                            )

                            // Road Label near 0,0
                            val roadText = "Road Level"
                            val roadTextWidth = paint.measureText(roadText)
                            val roadTextHeight = paint.textSize
                            val roadBoxWidth = roadTextWidth + 12f
                            val roadBoxHeight = roadTextHeight + 6f
                            val roadBoxLeft = toCX(xMin + 0.5f)
                            val roadBoxTop = toCY(0f) - 6f - roadBoxHeight / 2f - 2f
                            
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.9f),
                                topLeft = Offset(roadBoxLeft, roadBoxTop),
                                size = Size(roadBoxWidth, roadBoxHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                            drawRoundRect(
                                color = Color(0xFF64748B),
                                topLeft = Offset(roadBoxLeft, roadBoxTop),
                                size = Size(roadBoxWidth, roadBoxHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                                style = Stroke(width = 1f)
                            )

                            paint.color = Color(0xFF64748B).toArgb()
                            paint.textAlign = Paint.Align.LEFT
                            drawContext.canvas.nativeCanvas.drawText(
                                roadText,
                                roadBoxLeft + 6f,
                                roadBoxTop + roadBoxHeight / 2f + roadTextHeight / 2f - 1f,
                                paint
                            )

                            // Crest Label near peak
                            val crestText = "Ramp Crest (+${String.format("%.2f", report.crestHeight)} ft)"
                            val crestTextWidth = paint.measureText(crestText)
                            val crestTextHeight = paint.textSize
                            val crestBoxWidth = crestTextWidth + 14f
                            val crestBoxHeight = crestTextHeight + 8f
                            val crestBoxLeft = toCX(config.gutterWidth + config.upwardRampLength) - crestBoxWidth / 2f
                            val crestBoxTop = toCY(report.crestHeight) - 15f - crestBoxHeight / 2f - 4f
                            
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.95f),
                                topLeft = Offset(crestBoxLeft, crestBoxTop),
                                size = Size(crestBoxWidth, crestBoxHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                            )
                            drawRoundRect(
                                color = Color(0xFFD97706),
                                topLeft = Offset(crestBoxLeft, crestBoxTop),
                                size = Size(crestBoxWidth, crestBoxHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                                style = Stroke(width = 1f)
                            )

                            paint.color = Color(0xFFD97706).toArgb()
                            paint.textAlign = Paint.Align.CENTER
                            drawContext.canvas.nativeCanvas.drawText(
                                crestText,
                                toCX(config.gutterWidth + config.upwardRampLength),
                                crestBoxTop + crestBoxHeight / 2f + crestTextHeight / 2f - 1f,
                                paint
                            )

                            // --- LIVE HEADROOM INSPECTOR OVERLAY DRAWING ---
                            val inspCX = toCX(inspectorX)
                            val inspFloorY = RampCalculator.getRampHeight(inspectorX, config)
                            val inspFloorCY = toCY(inspFloorY)
                            val isInsideCovered = inspectorX in config.basementEntranceX..config.basementEndX
                            
                            if (isInsideCovered) {
                                val headroomVal = config.basementTopLevel - inspFloorY
                                val isBelowReq = headroomVal < config.requiredClearHeight
                                val inspLineColor = if (isBelowReq) Color(0xFFEF4444) else Color(0xFF8B5CF6)
                                
                                drawLine(
                                    color = inspLineColor,
                                    start = Offset(inspCX, inspFloorCY),
                                    end = Offset(inspCX, ceilYBottom),
                                    strokeWidth = 3f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                                drawCircle(color = inspLineColor, radius = 4f, center = Offset(inspCX, inspFloorCY))
                                drawCircle(color = inspLineColor, radius = 4f, center = Offset(inspCX, ceilYBottom))
                                
                                val headroomText = "HR: ${String.format("%.2f", headroomVal)} ft"
                                val hrTextWidth = paint.measureText(headroomText)
                                val hrTextHeight = paint.textSize
                                val hrBoxWidth = hrTextWidth + 20f
                                val hrBoxHeight = hrTextHeight + 12f
                                val hrBoxLeft = inspCX - hrBoxWidth / 2f
                                val hrBoxTop = (inspFloorCY + ceilYBottom) / 2f - hrBoxHeight / 2f - 2f
                                
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(hrBoxLeft, hrBoxTop),
                                    size = Size(hrBoxWidth, hrBoxHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                )
                                drawRoundRect(
                                    color = inspLineColor,
                                    topLeft = Offset(hrBoxLeft, hrBoxTop),
                                    size = Size(hrBoxWidth, hrBoxHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                                    style = Stroke(width = 2f)
                                )

                                paint.color = inspLineColor.toArgb()
                                paint.textAlign = Paint.Align.CENTER
                                paint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                                drawContext.canvas.nativeCanvas.drawText(
                                    headroomText,
                                    inspCX,
                                    hrBoxTop + hrBoxHeight / 2f + hrTextHeight / 2f - 2f,
                                    paint
                                )
                            } else {
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.4f),
                                    start = Offset(inspCX, inspFloorCY),
                                    end = Offset(inspCX, toCY(config.basementTopLevel)),
                                    strokeWidth = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                                drawCircle(color = Color.Gray.copy(alpha = 0.4f), radius = 3f, center = Offset(inspCX, inspFloorCY))
                                
                                val openAirText = "Open Air"
                                val oaTextWidth = paint.measureText(openAirText)
                                val oaTextHeight = paint.textSize
                                val oaBoxWidth = oaTextWidth + 20f
                                val oaBoxHeight = oaTextHeight + 12f
                                val oaBoxLeft = inspCX - oaBoxWidth / 2f
                                val oaBoxTop = inspFloorCY - 15f - oaBoxHeight / 2f - 5f
                                
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(oaBoxLeft, oaBoxTop),
                                    size = Size(oaBoxWidth, oaBoxHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                )
                                drawRoundRect(
                                    color = Color.Gray,
                                    topLeft = Offset(oaBoxLeft, oaBoxTop),
                                    size = Size(oaBoxWidth, oaBoxHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                                    style = Stroke(width = 1.5f)
                                )

                                paint.color = Color.Gray.toArgb()
                                paint.textAlign = Paint.Align.CENTER
                                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                                drawContext.canvas.nativeCanvas.drawText(
                                    openAirText,
                                    inspCX,
                                    oaBoxTop + oaBoxHeight / 2f + oaTextHeight / 2f - 2f,
                                    paint
                                )
                            }

                            // Visual hollow circle highlights for adjustable handles
                            // 1. Crest Handle
                            drawCircle(
                                color = Color(0xFFF59E0B),
                                radius = 9f,
                                center = Offset(toCX(crestX), toCY(report.crestHeight)),
                                style = Stroke(width = 2.5f)
                            )
                            // 2. Entrance Lip Handle
                            drawCircle(
                                color = Color(0xFF0284C7),
                                radius = 9f,
                                center = Offset(ceilStartX, ceilYBottom),
                                style = Stroke(width = 2.5f)
                            )
                            // 3. Basement End Handle
                            drawCircle(
                                color = Color(0xFF701A75),
                                radius = 9f,
                                center = Offset(ceilEndX, ceilYBottom),
                                style = Stroke(width = 2.5f)
                            )
                            // 4. Ceiling Height Handle (Middle)
                            drawCircle(
                                color = Color(0xFF10B981),
                                radius = 9f,
                                center = Offset((ceilStartX + ceilEndX) / 2f, ceilYBottom),
                                style = Stroke(width = 2.5f)
                            )
                            // 5. Headroom Inspector Handle
                            drawCircle(
                                color = Color(0xFF8B5CF6),
                                radius = 10f,
                                center = Offset(inspCX, inspFloorCY),
                                style = Stroke(width = 2.5f)
                            )

                            // Flood barrier water threat height (drawn outside road)
                            val waterPath = Path()
                            waterPath.moveTo(toCX(xMin), toCY(0f))
                            waterPath.lineTo(toCX(0f), toCY(0f))
                            waterPath.lineTo(toCX(0f), toCY(crestY))
                            waterPath.lineTo(toCX(xMin), toCY(crestY))
                            waterPath.close()
                            drawPath(
                                path = waterPath,
                                color = Color(0xFF60A5FA).copy(alpha = 0.25f)
                            )
                            
                            // 6. DRAW THE VEHICLE SIMULATION IN SCALE
                            val sim = simulationResult
                            val fWX = toCX(sim.frontWheelX)
                            val fWY = toCY(sim.frontWheelY)
                            val rWX = toCX(sim.rearWheelX)
                            val rWY = toCY(sim.rearWheelY)
                            
                            // Wheel sizes (approx 2 ft diameter / 1 ft radius)
                            val wheelRadiusPx = 0.9f * scale
                            
                            // Draw wheels
                            val hasAnyScrape = sim.underbodyScrapeAmount > 0f || sim.frontBumperScrape || sim.rearBumperScrape || sim.roofScrape
                            val wheelColor = if (hasAnyScrape) Color(0xFFEF4444) else Color(0xFF10B981)
                            
                            drawCircle(color = Color.Black, radius = wheelRadiusPx, center = Offset(fWX, fWY - wheelRadiusPx))
                            drawCircle(color = Color.Black, radius = wheelRadiusPx, center = Offset(rWX, rWY - wheelRadiusPx))
                            drawCircle(color = Color.LightGray, radius = wheelRadiusPx * 0.4f, center = Offset(fWX, fWY - wheelRadiusPx))
                            drawCircle(color = Color.LightGray, radius = wheelRadiusPx * 0.4f, center = Offset(rWX, rWY - wheelRadiusPx))

                            // Draw chassis bottom line
                            val ptChassisRearBottomX = toCX(sim.rearWheelX)
                            val ptChassisRearBottomY = toCY(sim.chassisBottomYAtRearWheel)
                            val ptChassisFrontBottomX = toCX(sim.frontWheelX)
                            val ptChassisFrontBottomY = toCY(sim.chassisBottomYAtFrontWheel)
                            
                            drawLine(
                                color = wheelColor,
                                start = Offset(ptChassisRearBottomX, ptChassisRearBottomY),
                                end = Offset(ptChassisFrontBottomX, ptChassisFrontBottomY),
                                strokeWidth = 4f
                            )

                            // Draw Bumpers
                            val fBX = toCX(sim.frontBumperX)
                            val fBY = toCY(sim.frontBumperY)
                            val rBX = toCX(sim.rearBumperX)
                            val rBY = toCY(sim.rearBumperY)

                            // Bumper lines connecting wheels
                            drawLine(color = wheelColor, start = Offset(fWX, fWY - wheelRadiusPx), end = Offset(fBX, fBY), strokeWidth = 4f)
                            drawLine(color = wheelColor, start = Offset(rWX, rWY - wheelRadiusPx), end = Offset(rBX, rBY), strokeWidth = 4f)

                            // Draw stylized car body cabin or Scooter with Rider
                            val angleCos = cos(sim.chassisAngleRad)
                            val angleSin = sin(sim.chassisAngleRad)
                            
                            if (activeVehicle.name == "Scooter with Rider") {
                                // Draw Scooter frame and Rider
                                val scooterPath = Path()
                                // Seat area coordinates
                                val seatX = toCX(sim.rearWheelX + 0.5f * angleCos)
                                val seatY = toCY(sim.rearWheelY + 2.0f) // seat height is around 2 feet
                                
                                // Floorboard area
                                val floorX = toCX(sim.rearWheelX + 1.8f * angleCos)
                                val floorY = toCY(sim.rearWheelY + 0.8f) // floorboard is low
                                
                                // Front column/handlebar height is around 3.2 feet
                                val stemX = toCX(sim.frontWheelX - 0.2f * angleCos)
                                val stemY = toCY(sim.frontWheelY + 3.2f)
                                
                                // Scooter body outline
                                scooterPath.moveTo(rBX, rBY)
                                scooterPath.lineTo(toCX(sim.rearWheelX), toCY(sim.rearWheelY + 1.6f))
                                scooterPath.lineTo(seatX, seatY)
                                scooterPath.lineTo(floorX, floorY)
                                scooterPath.lineTo(toCX(sim.frontWheelX - 0.5f * angleCos), toCY(sim.frontWheelY + 0.8f))
                                scooterPath.lineTo(stemX, stemY)
                                scooterPath.lineTo(fBX, fBY)
                                
                                drawPath(
                                    path = scooterPath,
                                    color = if (hasAnyScrape) Color(0xFFFCA5A5).copy(alpha = 0.6f) 
                                            else Color(0xFFE9D5FF).copy(alpha = 0.6f) // lavender/purple scooter
                                )
                                drawPath(
                                    path = scooterPath,
                                    color = if (hasAnyScrape) Color(0xFFEF4444) else Color(0xFF9333EA),
                                    style = Stroke(width = 3.5f)
                                )
                                
                                // Draw Rider stick figure sitting down
                                val hipX = seatX
                                val hipY = seatY - 5f // slightly down in pixels on the seat
                                
                                // Torso goes up to shoulder height (~4.3 feet)
                                val shoulderX = toCX(sim.rearWheelX + 0.7f * angleCos)
                                val shoulderY = toCY(sim.rearWheelY + 4.3f)
                                
                                // Head center is at activeVehicle.overallHeight (6.0 ft)
                                val headX = toCX(sim.rearWheelX + 0.8f * angleCos)
                                val headY = toCY(sim.rearWheelY + activeVehicle.overallHeight)
                                
                                // Draw Helmet/Head
                                drawCircle(
                                    color = if (hasAnyScrape) Color(0xFFEF4444) else Color(0xFF7E22CE),
                                    radius = 12f,
                                    center = Offset(headX, headY + 12f)
                                )
                                
                                // Draw Torso (Thicker stroke)
                                drawLine(
                                    color = if (hasAnyScrape) Color(0xFFEF4444) else Color(0xFF9333EA),
                                    start = Offset(hipX, hipY),
                                    end = Offset(shoulderX, shoulderY),
                                    strokeWidth = 6f
                                )
                                
                                // Draw Arm holding handlebar
                                drawLine(
                                    color = if (hasAnyScrape) Color(0xFFEF4444) else Color(0xFF9333EA),
                                    start = Offset(shoulderX, shoulderY),
                                    end = Offset(stemX, stemY),
                                    strokeWidth = 4f
                                )
                                
                                // Draw Leg to floorboard
                                drawLine(
                                    color = if (hasAnyScrape) Color(0xFFEF4444) else Color(0xFF9333EA),
                                    start = Offset(hipX, hipY),
                                    end = Offset(floorX, floorY),
                                    strokeWidth = 4f
                                )
                            } else {
                                val carCabinPath = Path()
                                val oh = activeVehicle.overallHeight
                                val rearRoofX = toCX(sim.rearWheelX + 1.0f * angleCos)
                                val rearRoofY = toCY(sim.rearWheelY + oh)
                                val frontRoofX = toCX(sim.frontWheelX - 1.2f * angleCos)
                                val frontRoofY = toCY(sim.frontWheelY + oh)
                                
                                carCabinPath.moveTo(rBX, rBY)
                                carCabinPath.lineTo(rWX, rWY - wheelRadiusPx * 1.5f)
                                carCabinPath.lineTo(rearRoofX, rearRoofY)
                                carCabinPath.lineTo(frontRoofX, frontRoofY)
                                carCabinPath.lineTo(fWX, fWY - wheelRadiusPx * 1.5f)
                                carCabinPath.lineTo(fBX, fBY)
                                carCabinPath.close()

                                drawPath(
                                    path = carCabinPath,
                                    color = if (hasAnyScrape) Color(0xFFFCA5A5).copy(alpha = 0.6f) 
                                            else Color(0xFF93C5FD).copy(alpha = 0.6f)
                                )
                                drawPath(
                                    path = carCabinPath,
                                    color = if (hasAnyScrape) Color(0xFFEF4444) else Color(0xFF2563EB),
                                    style = Stroke(width = 3f)
                                )
                            }

                            // Highlight underbody points that are scraping
                            sim.underbodyPoints.forEach { pt ->
                                if (pt.isScraping) {
                                    drawCircle(
                                        color = Color(0xFFEF4444),
                                        radius = 5f,
                                        center = Offset(toCX(pt.x), toCY(pt.rampY))
                                    )
                                }
                            }

                            // Highlighting bumper scraping
                            if (sim.frontBumperScrape) {
                                drawCircle(
                                    color = Color(0xFFEF4444),
                                    radius = 7f,
                                    center = Offset(fBX, fBY)
                                )
                            }
                            if (sim.rearBumperScrape) {
                                drawCircle(
                                    color = Color(0xFFEF4444),
                                    radius = 7f,
                                    center = Offset(rBX, rBY)
                                )
                            }
                        }

                        // --- INTERACTIVE CAM-ZOOM AND VIEWPORT CONTROLS OVERLAY ---
                        // 1. Focus Mode Selection (Top Left)
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                                .border(1.dp, Color.LightGray.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "Full" to "🌐 Full",
                                "Car" to "🚗 Car",
                                "Crest" to "📐 Crest",
                                "Entrance" to "🚪 Entry"
                            ).forEach { (modeKey, modeLabel) ->
                                val isSelected = focusMode == modeKey
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            RoundedCornerShape(20.dp)
                                        )
                                        .clickable {
                                            focusMode = modeKey
                                            if (modeKey == "Full") {
                                                zoomLevel = 1.0f
                                                panOffsetFeetX = 0f
                                                panOffsetFeetY = 0f
                                            } else {
                                                zoomLevel = 2.0f
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = modeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // 2. Zoom In / Out / Value Display Controls (Top Right)
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                                .border(1.dp, Color.LightGray.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Zoom Out Button
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        if (zoomLevel > 1.0f) MaterialTheme.colorScheme.secondaryContainer else Color.LightGray.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                                    .clickable(enabled = zoomLevel > 1.0f) {
                                        focusMode = "Manual"
                                        zoomLevel = (zoomLevel - 0.25f).coerceIn(1.0f, 3.5f)
                                        if (zoomLevel == 1.0f) {
                                            focusMode = "Full"
                                            panOffsetFeetX = 0f
                                            panOffsetFeetY = 0f
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("−", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            
                            // Zoom Value
                            Text(
                                text = "${String.format("%.1f", zoomLevel)}x",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                modifier = Modifier.padding(horizontal = 6.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            // Zoom In Button
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        if (zoomLevel < 3.5f) MaterialTheme.colorScheme.secondaryContainer else Color.LightGray.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                                    .clickable(enabled = zoomLevel < 3.5f) {
                                        if (focusMode == "Full") {
                                            focusMode = "Manual"
                                        }
                                        zoomLevel = (zoomLevel + 0.25f).coerceIn(1.0f, 3.5f)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }

                        // 3. Helpful interactive user tip (Bottom Center/Left)
                        if (zoomLevel > 1.0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "↕️ Drag background to pan • Use circles to edit",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Clearance Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ClearanceBadge(
                            label = "Underbelly",
                            scrapeAmount = simulationResult.underbodyScrapeAmount,
                            modifier = Modifier.weight(1f)
                        )
                        ClearanceBadge(
                            label = "Front Bumper",
                            scrapeAmount = if (simulationResult.frontBumperScrape) simulationResult.frontBumperScrapeAmount else 0f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ClearanceBadge(
                            label = "Rear Bumper",
                            scrapeAmount = if (simulationResult.rearBumperScrape) simulationResult.rearBumperScrapeAmount else 0f,
                            modifier = Modifier.weight(1f)
                        )
                        ClearanceBadge(
                            label = "Overhead Roof",
                            scrapeAmount = if (simulationResult.roofScrape) simulationResult.roofScrapeAmount else 0f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Car Position Driver Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Drive Car:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(80.dp)
                        )
                        Slider(
                            value = carPositionX,
                            onValueChange = { carPositionX = it },
                            valueRange = -6.0f..maxScrollDist,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("car_driver_slider")
                        )
                        Text(
                            text = "${String.format("%.1f", carPositionX)} ft",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(50.dp),
                            textAlign = TextAlign.End
                        )
                    }

                    // Contextual warnings
                    if (hasAnyScrape) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SCRAPING DETECTED! Try turning on Transition Curves or easing slopes.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0xFFE6F4EA),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Safe",
                                    tint = Color(0xFF137333)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Perfect Clearance! The vehicle clears the ramp at this point.",
                                    color = Color(0xFF137333),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- LIVE HEADROOM INSPECTOR CARD ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📏", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Headroom Inspector",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Drag the purple ruler handle on the canvas or use the slider below to inspect headroom (ceiling clearance) at any point along the ramp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    val floorHeight = RampCalculator.getRampHeight(inspectorX, config)
                    val isInsideCovered = inspectorX in config.basementEntranceX..config.basementEndX
                    val headroom = if (isInsideCovered) config.basementTopLevel - floorHeight else -1f
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isInsideCovered) {
                                    if (headroom < config.requiredClearHeight) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    else Color(0xFFE2F0D9)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "📍 Inspected Point: ${String.format("%.1f", inspectorX)} feet from road",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FLOOR LEVEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                val floorSign = if (floorHeight < 0) "below road" else if (floorHeight > 0) "above road" else "road level"
                                Text(
                                    text = "${String.format("%.2f", floorHeight)} ft (${String.format("%.1f", Math.abs(floorHeight * 12f))} in $floorSign)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Text(
                                    text = "CEILING LEVEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isInsideCovered) "${String.format("%.2f", config.basementTopLevel)} ft (${String.format("%.1f", config.basementTopLevel * 12f)} in above road)" else "Open Air (No Ceiling)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FLOOR-TO-CEILING HEIGHT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isInsideCovered) "${String.format("%.2f", headroom)} ft (${String.format("%.1f", headroom * 12f)} in)" else "Unlimited (Open Air)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isInsideCovered) {
                                        if (headroom < config.requiredClearHeight) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Text(
                                    text = "REQUIRED HEIGHT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${String.format("%.2f", config.requiredClearHeight)} ft (${String.format("%.0f", config.requiredClearHeight * 12f)} in)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusIcon = if (isInsideCovered) {
                                if (headroom < config.requiredClearHeight) Icons.Default.Warning else Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Info
                            }
                            val statusColor = if (isInsideCovered) {
                                if (headroom < config.requiredClearHeight) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = "Status Icon",
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isInsideCovered) {
                                    if (headroom < config.requiredClearHeight) "⚠️ SCRAPE RISK: Clearance is deficient by ${String.format("%.1f", (config.requiredClearHeight - headroom) * 12f)} inches!"
                                    else "✅ SAFE CLEARANCE: Clearance exceeds requirement by ${String.format("%.1f", (headroom - config.requiredClearHeight) * 12f)} inches."
                                } else {
                                    "✨ OPEN SKY ZONE: Free overhead clearance. Fully safe."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = inspectorX,
                        onValueChange = { inspectorX = it },
                        valueRange = 0.0f..maxScrollDist,
                        modifier = Modifier.fillMaxWidth().testTag("headroom_inspector_slider")
                    )
                }
            }
        }

        // --- PROBABLE RAMP DESIGNS FOR COMFORT & CLEARANCE ---
        item {
            val safeOptions = remember(config) { RampCalculator.generateSafeDesigns(config) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Probable Safe Ramp Designs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (safeOptions.isNotEmpty()) Color(0xFFE2F0D9) else Color(0xFFFEE2E2),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (safeOptions.isNotEmpty()) "${safeOptions.size} Solutions Found" else "No perfect layouts",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (safeOptions.isNotEmpty()) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Based on your current required clear height of ${String.format("%.1f", config.requiredClearHeight)} ft, these calculated geometries guarantee 100% safe clearance (0 scraping) for scooters (with rider), hatchbacks, and SUVs:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (safeOptions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF2F2), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "⚠️ No completely safe alternative designs found for a ${String.format("%.1f", config.requiredClearHeight)} ft clear height inside a ${String.format("%.1f", config.basementTopLevel)} ft ceiling. Try slightly decreasing the Required Clear Height or increasing the Basement Ceiling Level to find auto-optimized profiles.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC62828)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            safeOptions.forEach { opt ->
                                val isCurrent = Math.abs(config.upwardSlopeRatioDenominator - opt.upwardSlopeRatioDenominator) < 0.05f &&
                                                Math.abs(config.downwardSlopeRatioDenominator - opt.downwardSlopeRatioDenominator) < 0.05f &&
                                                Math.abs(config.basementEntranceX - opt.basementEntranceX) < 0.05f
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            width = if (isCurrent) 2.dp else 1.dp,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            // Auto-apply this safe design!
                                            config = config.copy(
                                                upwardSlopeRatioDenominator = opt.upwardSlopeRatioDenominator,
                                                downwardSlopeRatioDenominator = opt.downwardSlopeRatioDenominator,
                                                basementEntranceX = opt.basementEntranceX,
                                                basementEndX = maxOf(config.basementEndX, opt.basementEntranceX + 15f)
                                            )
                                        }
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = opt.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (opt.isRecommended) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFE2F0D9), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "RECOMMENDED",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = opt.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Specific parameters
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DesignParamBadge(label = "Upward Slope", value = "1:${opt.upwardSlopeRatioDenominator.toInt()}")
                                            DesignParamBadge(label = "Downward Slope", value = "1:${opt.downwardSlopeRatioDenominator.toInt()}")
                                            DesignParamBadge(label = "Roof Starts", value = "${opt.basementEntranceX.toInt()} ft")
                                            DesignParamBadge(label = "Total Run", value = "${String.format("%.1f", opt.totalHorizontalLength)} ft")
                                        }
                                        
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "✨ Active Configuration Applied",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "👉 Tap to Apply Design Pattern",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. GEOMETRIC CONFIGURATION PANEL (EXPANDABLE) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSlopeSettings = !showSlopeSettings },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Customize Ramp Geometry Settings",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(if (showSlopeSettings) "▲" else "▼", fontWeight = FontWeight.Bold)
                    }

                    AnimatedVisibility(
                        visible = showSlopeSettings,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            // Profile Mode Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE2E8F0))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val isCustom = config.useCustomSegments
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (!isCustom) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { config = config.copy(useCustomSegments = false) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Standard Incline Mode",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isCustom) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isCustom) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { config = config.copy(useCustomSegments = true) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Custom Point-to-Point",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCustom) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (config.useCustomSegments) {
                                // CUSTOM POINT-TO-POINT SEGMENT BUILDER
                                Text(
                                    text = "Custom Slope Segment Sequences",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = "Add, remove, and fine-tune individual slope segments below. The canvas and simulation update dynamically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                config.segments.forEachIndexed { index, segment ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Segment #${index + 1}: ${segment.name}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                
                                                if (config.segments.size > 1) {
                                                    Text(
                                                        text = "Delete",
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier
                                                            .clickable {
                                                                val updated = config.segments.filterIndexed { i, _ -> i != index }
                                                                config = config.copy(segments = updated)
                                                            }
                                                            .padding(4.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Length Slider
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Horizontal Run Length:", style = MaterialTheme.typography.bodySmall)
                                                Text("${String.format("%.1f", segment.length)} ft", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Slider(
                                                value = segment.length,
                                                onValueChange = { newL ->
                                                    val updated = config.segments.mapIndexed { i, s ->
                                                        if (i == index) s.copy(length = newL) else s
                                                    }
                                                    config = config.copy(segments = updated)
                                                },
                                                valueRange = 0.5f..20.0f
                                            )

                                            // Slope Slider
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                val pct = segment.slopeRatio * 100f
                                                val slopeText = when {
                                                    segment.slopeRatio > 0f -> "Upward +${String.format("%.1f", pct)}% (1 : ${String.format("%.1f", 1f/segment.slopeRatio)})"
                                                    segment.slopeRatio < 0f -> "Downward ${String.format("%.1f", pct)}% (1 : ${String.format("%.1f", -1f/segment.slopeRatio)})"
                                                    else -> "Flat (0.0%)"
                                                }
                                                Text("Incline Slope:", style = MaterialTheme.typography.bodySmall)
                                                Text(slopeText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Slider(
                                                value = segment.slopeRatio,
                                                onValueChange = { newS ->
                                                    val updated = config.segments.mapIndexed { i, s ->
                                                        if (i == index) s.copy(slopeRatio = newS) else s
                                                    }
                                                    config = config.copy(segments = updated)
                                                },
                                                valueRange = -0.35f..0.35f
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Add Segment Button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            val newId = (config.segments.size + 1).toString()
                                            val lastSeg = config.segments.lastOrNull()
                                            val nextSlope = lastSeg?.slopeRatio ?: 0f
                                            val newSeg = RampSegment(
                                                id = newId,
                                                name = "Incline Section $newId",
                                                length = 4.0f,
                                                slopeRatio = nextSlope
                                            )
                                            config = config.copy(segments = config.segments + newSeg)
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+ Add New Slope Segment",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                // Required Entrance Height & Top Level in Custom Mode as well
                                Text(
                                    text = "Basement Entrance Clear Opening Height: ${String.format("%.1f", config.requiredClearHeight)} feet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = config.requiredClearHeight,
                                    onValueChange = { config = config.copy(requiredClearHeight = it) },
                                    valueRange = 6.5f..9.0f
                                )

                                Text(
                                    text = "Basement Roof Ceiling Level (relative to road): +${String.format("%.1f", config.basementTopLevel)} feet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = config.basementTopLevel,
                                    onValueChange = { config = config.copy(basementTopLevel = it) },
                                    valueRange = 3.0f..7.0f
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Basement Entrance X Position Slider
                                Text(
                                    text = "Basement Entrance (Starts after drain end): ${String.format("%.1f", config.basementEntranceX)} feet from road",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = config.basementEntranceX,
                                    onValueChange = { 
                                        val newEntranceX = it
                                        val newEndX = maxOf(config.basementEndX, newEntranceX + 2.0f)
                                        config = config.copy(basementEntranceX = newEntranceX, basementEndX = newEndX)
                                    },
                                    valueRange = 4.0f..35.0f,
                                    modifier = Modifier.testTag("basement_entrance_slider_custom")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Basement End X Position Slider
                                Text(
                                    text = "Basement End Point: ${String.format("%.1f", config.basementEndX)} feet from road",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = config.basementEndX,
                                    onValueChange = { 
                                        config = config.copy(basementEndX = maxOf(it, config.basementEntranceX + 2.0f))
                                    },
                                    valueRange = 15.0f..60.0f,
                                    modifier = Modifier.testTag("basement_end_slider_custom")
                                )
                            } else {
                                // Flood Barrier / Upward Slope Ratio
                                Text(
                                    text = "Upward Ramp (Flood Barrier) Slope: 1 : ${String.format("%.1f", config.upwardSlopeRatioDenominator)} " +
                                            "(${String.format("%.1f", config.upwardSlopeRatio * 100)}%)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = config.upwardSlopeRatioDenominator,
                                    onValueChange = { config = config.copy(upwardSlopeRatioDenominator = it) },
                                    valueRange = 3.0f..10.0f,
                                    modifier = Modifier.testTag("upward_slope_slider")
                                )

                                // Downward Slope Ratio
                                Text(
                                    text = "Downward Ramp Slope: 1 : ${String.format("%.1f", config.downwardSlopeRatioDenominator)} " +
                                            "(${String.format("%.1f", config.downwardSlopeRatio * 100)}%)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = config.downwardSlopeRatioDenominator,
                                    onValueChange = { config = config.copy(downwardSlopeRatioDenominator = it) },
                                    valueRange = 5.0f..15.0f,
                                    modifier = Modifier.testTag("downward_slope_slider")
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                // Required Entrance Height
                                Text(
                                    text = "Basement Entrance Clear Opening Height: ${String.format("%.1f", config.requiredClearHeight)} feet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = config.requiredClearHeight,
                                    onValueChange = { config = config.copy(requiredClearHeight = it) },
                                    valueRange = 6.5f..9.0f
                                )

                                // Basement Slab Top Height
                                Text(
                                    text = "Basement Roof Ceiling Level (relative to road): +${String.format("%.1f", config.basementTopLevel)} feet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = config.basementTopLevel,
                                    onValueChange = { config = config.copy(basementTopLevel = it) },
                                    valueRange = 3.0f..7.0f
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Basement Entrance X Position Slider
                                Text(
                                    text = "Basement Entrance (Starts after drain end): ${String.format("%.1f", config.basementEntranceX)} feet from road",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = config.basementEntranceX,
                                    onValueChange = { 
                                        val newEntranceX = it
                                        val newEndX = maxOf(config.basementEndX, newEntranceX + 2.0f)
                                        config = config.copy(basementEntranceX = newEntranceX, basementEndX = newEndX)
                                    },
                                    valueRange = 4.0f..35.0f,
                                    modifier = Modifier.testTag("basement_entrance_slider_standard")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Basement End X Position Slider
                                Text(
                                    text = "Basement End Point: ${String.format("%.1f", config.basementEndX)} feet from road",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = config.basementEndX,
                                    onValueChange = { 
                                        config = config.copy(basementEndX = maxOf(it, config.basementEntranceX + 2.0f))
                                    },
                                    valueRange = 15.0f..60.0f,
                                    modifier = Modifier.testTag("basement_end_slider_standard")
                                )

                                // Covered Gutter & Upward Rise lengths
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Gutter Width: ${String.format("%.1f", config.gutterWidth)} ft", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Slider(
                                            value = config.gutterWidth,
                                            onValueChange = { config = config.copy(gutterWidth = it) },
                                            valueRange = 1.0f..4.0f
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Peak Ascent: ${String.format("%.1f", config.upwardRampLength)} ft", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Slider(
                                            value = config.upwardRampLength,
                                            onValueChange = { config = config.copy(upwardRampLength = it) },
                                            valueRange = 1.0f..5.0f
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // Transition Easements toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Apply Smooth Transition Curves",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Rounds off sharp slope changes at the crest & bottom to prevent vehicle grounding.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Switch(
                                    checked = config.applyTransition,
                                    onCheckedChange = { config = config.copy(applyTransition = it) },
                                    modifier = Modifier.testTag("transition_switch")
                                )
                            }

                            if (config.applyTransition) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Transition Curve Lengths (Easements):",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Crest: ${String.format("%.1f", config.transitionCrestLength)} ft", style = MaterialTheme.typography.bodySmall)
                                        Slider(
                                            value = config.transitionCrestLength,
                                            onValueChange = { config = config.copy(transitionCrestLength = it) },
                                            valueRange = 2.0f..6.0f
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Bottom: ${String.format("%.1f", config.transitionBottomLength)} ft", style = MaterialTheme.typography.bodySmall)
                                        Slider(
                                            value = config.transitionBottomLength,
                                            onValueChange = { config = config.copy(transitionBottomLength = it) },
                                            valueRange = 2.0f..6.0f
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5. TECHNICAL SUMMARY & REPORT CARD ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📐 Design Profiles & Geometry Report",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            ReportItem(label = "Flood Barrier Height (Peak)", value = "${String.format("%.1f", report.crestHeight * 12f)} inches (${String.format("%.2f", report.crestHeight)} ft)")
                            ReportItem(label = "Basement Floor Level", value = "${String.format("%.2f", report.basementFloorLevel)} ft below road")
                            ReportItem(label = "Downward Vertical Drop", value = "${String.format("%.2f", report.downwardVerticalDrop)} ft")
                            ReportItem(label = "Basement Entrance Point", value = "${String.format("%.1f", config.basementEntranceX)} ft from road")
                            val hAtEntrance = config.basementTopLevel - RampCalculator.getRampHeight(config.basementEntranceX, config)
                            ReportItem(label = "Headroom at Entrance", value = "${String.format("%.2f", hAtEntrance)} ft")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            ReportItem(label = "Downward Horizontal Run", value = "${String.format("%.2f", report.downwardHorizontalRun)} ft")
                            ReportItem(label = "Total Ramp Sloped Length", value = "${String.format("%.2f", report.totalHorizontalLength)} ft")
                            ReportItem(label = "Basement End Point", value = "${String.format("%.1f", config.basementEndX)} ft from road")
                            val isInsideCovered = inspectorX in config.basementEntranceX..config.basementEndX
                            val hAtInspector = if (isInsideCovered) config.basementTopLevel - RampCalculator.getRampHeight(inspectorX, config) else -1f
                            val hAtInspectorText = if (isInsideCovered) "${String.format("%.2f", hAtInspector)} ft" else "Open Air"
                            ReportItem(label = "Inspector Point (${String.format("%.1f", inspectorX)} ft)", value = hAtInspectorText)
                            ReportItem(label = "Total Ramp Surface Length", value = "${String.format("%.2f", report.totalSurfaceLength)} ft")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Slope Grades: Upward Barrier ${String.format("%.1f", report.upwardSlopePercent)}% | Downward Ramp ${String.format("%.1f", report.downwardSlopePercent)}% " +
                                    "\nCumulative Slope Change at Crest: ${String.format("%.1f", report.breakoverAngleDegrees)}°",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // --- 6. MATERIALS & CIVIL ESTIMATOR CARD (EXPANDABLE) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMaterialSettings = !showMaterialSettings },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🧱 ", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Concrete & Reinforcement Materials",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(if (showMaterialSettings) "▲" else "▼", fontWeight = FontWeight.Bold)
                    }

                    AnimatedVisibility(
                        visible = showMaterialSettings,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            // Material Config Inputs
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Ramp Width: ${String.format("%.1f", config.rampWidth)} ft", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = config.rampWidth,
                                        onValueChange = { config = config.copy(rampWidth = it) },
                                        valueRange = 10.0f..18.0f
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Slab Thickness: ${String.format("%.1f", config.slabThicknessInches)} inches", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = config.slabThicknessInches,
                                        onValueChange = { config = config.copy(slabThicknessInches = it) },
                                        valueRange = 4.0f..10.0f
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Steel Density: ${String.format("%.0f", config.steelDensityKgPerM3)} kg/m³", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = config.steelDensityKgPerM3,
                                        onValueChange = { config = config.copy(steelDensityKgPerM3 = it) },
                                        valueRange = 40.0f..120.0f
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Concrete Mix Grade:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                            .padding(2.dp)
                                    ) {
                                        listOf("M15", "M20", "M25").forEach { grade ->
                                            val isSelected = config.concreteMixRatio.startsWith(grade)
                                            val fullGradeName = when (grade) {
                                                "M15" -> "M15 (1:2:4)"
                                                "M25" -> "M25 (1:1:2)"
                                                else -> "M20 (1:1.5:3)"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable { config = config.copy(concreteMixRatio = fullGradeName) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = grade,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // Materials Output List
                            Text(
                                text = "Estimated Material Proportions (Wet & Dry Sizing):",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            MaterialProportionRow(label = "Total Concrete Volume", value1 = "${String.format("%.1f", report.concreteVolumeCFT)} CFT", value2 = "${String.format("%.2f", report.concreteVolumeCum)} m³")
                            MaterialProportionRow(label = "Sub-base Gravel Volume", value1 = "${String.format("%.1f", report.subbaseVolumeCFT)} CFT", value2 = "${String.format("%.2f", report.subbaseVolumeCum)} m³")
                            MaterialProportionRow(label = "Cement Required", value1 = "${report.cementBags} Bags", value2 = "approx. ${report.cementBags * 50} kg")
                            MaterialProportionRow(label = "Sand Required", value1 = "${String.format("%.1f", report.sandCFT)} CFT", value2 = "approx. ${String.format("%.1f", report.sandCFT * 45)} kg")
                            MaterialProportionRow(label = "Aggregate Required", value1 = "${String.format("%.1f", report.aggregateCFT)} CFT", value2 = "approx. ${String.format("%.1f", report.aggregateCFT * 50)} kg")
                            MaterialProportionRow(label = "Steel Reinforcement Weight", value1 = "${String.format("%.1f", report.steelWeightKg)} kg", value2 = "${String.format("%.2f", report.steelWeightKg / 1000f)} Metric Tonnes")
                        }
                    }
                }
            }
        }

        // --- 7. VEHICLE HURDLES & DESIGN SOLUTIONS ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ Small Car Hurdles & Solutions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    HurdleItem(
                        icon = "🧗",
                        title = "1. Engine Stall / Low Low-end Torque",
                        description = "Small engine hatchbacks lack low-speed climbing torque. Stopping mid-slope on 1:5 or 1:8 incline makes it extremely hard to launch without stalling or excessive clutch burning."
                    )
                    HurdleItem(
                        icon = "🌧️",
                        title = "2. Wet Incline Wheel Slippage",
                        description = "Because small cars are typically Front-Wheel Drive (FWD), gravity transfers vehicle weight to the rear during climbing. This reduces normal force on front drive tires, causing massive wheel spin if the ramp is wet."
                    )
                    HurdleItem(
                        icon = "🩹",
                        title = "3. Underbody / Chassis Scrape at Crest",
                        description = "Without rounded transitions, transitioning instantly from a 1:5 ascent to a 1:8 descent forms a sharp 32.5% slope gap. Vehicles with under 6 inches clearance will high-center and scrape."
                    )
                    HurdleItem(
                        icon = "💥",
                        title = "4. Front Bumper Scrape at Bottom",
                        description = "At the end of the 1:8 slope, the sudden flat transition causes the vehicle's front overhang to plunge into the slab floor, scraping the front bumper."
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "🛠️ Recommended Engineering Solutions:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SolutionItem(text = "Apply 4-ft long transition curves at crest and bottom (turn on 'Transition Curves' toggle above to witness impact).")
                    SolutionItem(text = "Ramp Surface Grooving: Cast diagonal chevron grooves (1\" wide, 0.4\" deep, spaced at 4\" intervals) to maximize wet tire grip.")
                    SolutionItem(text = "Bottom Drainage Grate: Install an 8\" wide trench drain at the basement entry linked to a high-capacity sump pump to handle heavy runoff.")
                    SolutionItem(text = "Textured Concrete: Apply an acid-etched or rough broom concrete finish to maximize friction.")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun VehicleSpecBadge(label: String, value: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun DesignParamBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun ClearanceBadge(label: String, scrapeAmount: Float, modifier: Modifier = Modifier) {
    val isScraping = scrapeAmount > 0f
    val bg = if (isScraping) Color(0xFFFDE8E8) else Color(0xFFDEF7EC)
    val textCol = if (isScraping) Color(0xFF9B1C1C) else Color(0xFF03543F)
    
    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = textCol.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isScraping) "SCRAPES! (${String.format("%.1f", scrapeAmount * 12f)}\")" else "CLEAR",
            style = MaterialTheme.typography.bodySmall,
            color = textCol,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun ReportItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun MaterialProportionRow(label: String, value1: String, value2: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value1,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(90.dp),
            textAlign = TextAlign.End
        )
        Text(
            text = value2,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(110.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun HurdleItem(icon: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 18.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun SolutionItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = "▪", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 15.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RampDesignerScreenPreview() {
    MyApplicationTheme {
        RampDesignerScreen()
    }
}

