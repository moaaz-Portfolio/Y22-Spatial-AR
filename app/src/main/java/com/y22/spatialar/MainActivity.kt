package com.y22.spatialar

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.hypot

// -----------------------------------------------------------------------------
// Activity
// -----------------------------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SpatialHome(
                    lifecycleOwner = this@MainActivity,
                    onVrModeChange = { enabled -> applyVrMode(enabled) }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyVrMode(enabled: Boolean) {
        requestedOrientation = if (enabled) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        window.decorView.systemUiVisibility = if (enabled) {
            (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        } else {
            View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}

// -----------------------------------------------------------------------------
// Persistent VR settings (the existing calibration values are preserved)
// -----------------------------------------------------------------------------

private data class VrSettings(
    val eyeGap: Float = 0.045f,
    val zoom: Float = 1.0f,
    val leftScale: Float = 1.0f,
    val leftX: Float = 0.0f,
    val leftY: Float = 0.0f,
    val rightScale: Float = 1.0f,
    val rightX: Float = 0.0f,
    val rightY: Float = 0.0f,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val vignette: Float = 0.12f,
    val mirror: Boolean = false
)

private fun loadVrSettings(context: Context): VrSettings {
    val p = context.getSharedPreferences("y22_vr_settings", Context.MODE_PRIVATE)
    return VrSettings(
        eyeGap = p.getFloat("eyeGap", 0.045f),
        zoom = p.getFloat("zoom", 1.0f),
        leftScale = p.getFloat("leftScale", 1.0f),
        leftX = p.getFloat("leftX", 0.0f),
        leftY = p.getFloat("leftY", 0.0f),
        rightScale = p.getFloat("rightScale", 1.0f),
        rightX = p.getFloat("rightX", 0.0f),
        rightY = p.getFloat("rightY", 0.0f),
        brightness = p.getFloat("brightness", 0.0f),
        contrast = p.getFloat("contrast", 1.0f),
        saturation = p.getFloat("saturation", 1.0f),
        vignette = p.getFloat("vignette", 0.12f),
        mirror = p.getBoolean("mirror", false)
    )
}

private fun saveVrSettings(context: Context, s: VrSettings) {
    context.getSharedPreferences("y22_vr_settings", Context.MODE_PRIVATE).edit()
        .putFloat("eyeGap", s.eyeGap)
        .putFloat("zoom", s.zoom)
        .putFloat("leftScale", s.leftScale)
        .putFloat("leftX", s.leftX)
        .putFloat("leftY", s.leftY)
        .putFloat("rightScale", s.rightScale)
        .putFloat("rightX", s.rightX)
        .putFloat("rightY", s.rightY)
        .putFloat("brightness", s.brightness)
        .putFloat("contrast", s.contrast)
        .putFloat("saturation", s.saturation)
        .putFloat("vignette", s.vignette)
        .putBoolean("mirror", s.mirror)
        .apply()
}

// -----------------------------------------------------------------------------
// Floating windows and hand-control models
// -----------------------------------------------------------------------------

private data class SpatialWindow(
    val id: Int,
    val title: String,
    val message: String,
    val x: Float,
    val y: Float
)

/** Coordinates are normalized to the processed camera bitmap (0..1). */
private data class HandControl(
    val x: Float,
    val y: Float,
    val isPinching: Boolean,
    val isOpenPalm: Boolean,
    val timestampMs: Long
)

private fun distance2d(ax: Float, ay: Float, bx: Float, by: Float): Float {
    return hypot(ax - bx, ay - by)
}

// -----------------------------------------------------------------------------
// Main screen
// -----------------------------------------------------------------------------

@Composable
fun SpatialHome(
    lifecycleOwner: LifecycleOwner,
    onVrModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val targetRotation = remember(configuration.orientation) {
        @Suppress("DEPRECATION")
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.rotation
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraEnabled by remember { mutableStateOf(true) }
    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var vrMode by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(loadVrSettings(context)) }

    var showFloatingWindows by remember { mutableStateOf(false) }
    var handTrackingEnabled by remember { mutableStateOf(false) }
    var handControl by remember { mutableStateOf<HandControl?>(null) }
    var handError by remember { mutableStateOf<String?>(null) }

    val floatingWindows = remember {
        mutableStateListOf(
            SpatialWindow(
                id = 1,
                title = "لوحة النظام",
                message = "مرحباً بك في المساحة المكانية",
                x = 0.045f,
                y = 0.16f
            ),
            SpatialWindow(
                id = 2,
                title = "الملاحظات",
                message = "اسحب شريط العنوان لتحريك النافذة",
                x = 0.045f,
                y = 0.52f
            )
        )
    }

    var nextWindowId by remember { mutableStateOf(3) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        cameraError = if (granted) null else "لم يتم السماح باستخدام الكاميرا."
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(handTrackingEnabled) {
        if (!handTrackingEnabled) {
            handControl = null
            handError = null
        }
    }

    val updateSettings: (VrSettings) -> Unit = { next ->
        settings = next
        saveVrSettings(context, next)
    }

    val addFloatingWindow: () -> Unit = {
        val count = floatingWindows.size
        floatingWindows.add(
            SpatialWindow(
                id = nextWindowId,
                title = "نافذة $nextWindowId",
                message = "اسحب شريط العنوان أو استخدم إيماءة القرص باليد",
                x = (0.04f + (count % 3) * 0.06f).coerceAtMost(0.32f),
                y = (0.18f + (count % 4) * 0.13f).coerceAtMost(0.68f)
            )
        )
        nextWindowId += 1
        showFloatingWindows = true
    }

    val moveFloatingWindow: (Int, Float, Float, Float, Float) -> Unit =
        { id, dx, dy, maxX, maxY ->
            val index = floatingWindows.indexOfFirst { it.id == id }
            if (index >= 0) {
                val old = floatingWindows[index]
                floatingWindows[index] = old.copy(
                    x = (old.x + dx).coerceIn(0f, maxX),
                    y = (old.y + dy).coerceIn(0f, maxY)
                )
            }
        }

    val closeFloatingWindow: (Int) -> Unit = { id ->
        floatingWindows.removeAll { it.id == id }
    }

    if (hasCameraPermission && cameraEnabled) {
        CameraFrameSource(
            lifecycleOwner = lifecycleOwner,
            targetRotation = targetRotation,
            handTrackingEnabled = handTrackingEnabled,
            onFrame = { bitmap -> cameraBitmap = bitmap },
            onHandControl = { control -> handControl = control },
            onCameraError = { message -> cameraError = message },
            onHandError = { message -> handError = message }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (vrMode) {
            // The same virtual scene is drawn once for each eye viewport.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val gap = maxWidth * settings.eyeGap
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        EyeViewport(
                            bitmap = cameraBitmap,
                            settings = settings,
                            eyeScale = settings.leftScale,
                            offsetX = settings.leftX,
                            offsetY = settings.leftY,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        EyeViewport(
                            bitmap = cameraBitmap,
                            settings = settings,
                            eyeScale = settings.rightScale,
                            offsetX = settings.rightX,
                            offsetY = settings.rightY,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        vrMode = false
                        showSettings = false
                        showFloatingWindows = false
                        onVrModeChange(false)
                    }
                ) { Text("خروج") }

                Button(onClick = { showSettings = !showSettings }) {
                    Text("إعدادات VR")
                }

                Button(
                    onClick = { showFloatingWindows = !showFloatingWindows }
                ) {
                    Text(if (showFloatingWindows) "إخفاء" else "إظهار")
                }

                Button(onClick = addFloatingWindow) {
                    Text("نافذة +")
                }

                Button(
                    onClick = {
                        handTrackingEnabled = !handTrackingEnabled
                        handControl = null
                        handError = null
                    }
                ) {
                    Text(if (handTrackingEnabled) "إيقاف اليد" else "تشغيل اليد")
                }
            }
        } else {
            EyeViewport(
                bitmap = cameraBitmap,
                settings = settings,
                eyeScale = settings.leftScale,
                offsetX = settings.leftX,
                offsetY = settings.leftY,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x77071426),
                                Color(0x55101F38),
                                Color(0xDD030A14)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Y22 SPATIAL AR",
                    color = Color(0xFF55DFFF),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "مرحباً بك في عالمك المكاني",
                    color = Color.White,
                    fontSize = 20.sp
                )
                Spacer(Modifier.size(24.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xDD142B46)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "لوحة التحكم الرئيسية",
                            color = Color(0xFF55DFFF),
                            fontSize = 18.sp
                        )
                        Text(
                            text = when {
                                cameraError != null -> cameraError!!
                                cameraBitmap != null -> "الكاميرا الخلفية تعمل."
                                !hasCameraPermission -> "نحتاج إذن الكاميرا لبدء العرض."
                                else -> "جاري تشغيل الكاميرا..."
                            },
                            color = Color.White
                        )
                        Button(
                            onClick = {
                                vrMode = true
                                showFloatingWindows = true
                                onVrModeChange(true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("تشغيل وضع VR Box") }

                        OutlinedButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("الإعدادات المتقدمة") }

                        OutlinedButton(
                            onClick = addFloatingWindow,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("إضافة نافذة عائمة") }

                        OutlinedButton(
                            onClick = { showFloatingWindows = !showFloatingWindows },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (showFloatingWindows) "إخفاء النوافذ" else "إظهار النوافذ")
                        }

                        OutlinedButton(
                            onClick = {
                                handTrackingEnabled = !handTrackingEnabled
                                handControl = null
                                handError = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (handTrackingEnabled) "إيقاف التحكم باليد" else "تشغيل التحكم باليد")
                        }

                        if (handTrackingEnabled) {
                            Text(
                                text = handError ?: when {
                                    handControl?.isPinching == true -> "اليد مكتشفة: إمساك نافذة"
                                    handControl?.isOpenPalm == true -> "اليد مكتشفة: كف مفتوح"
                                    handControl != null -> "اليد مكتشفة: قرّب الإبهام والسبابة للسحب"
                                    else -> "وجّه يدك أمام الكاميرا..."
                                },
                                color = Color(0xFF9BEAFF),
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (!hasCameraPermission) {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                } else {
                                    cameraEnabled = !cameraEnabled
                                    cameraBitmap = null
                                    cameraError = null
                                    handControl = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (cameraEnabled) "إيقاف الكاميرا" else "تشغيل الكاميرا")
                        }

                        if (!hasCameraPermission) {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("السماح باستخدام الكاميرا") }
                        }
                    }
                }
            }
        }

        // Floating windows are drawn above both eye images. In VR the same layout
        // is mirrored into both eye viewports; only the left copy handles input.
        if (showFloatingWindows && floatingWindows.isNotEmpty()) {
            if (vrMode) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val gap = maxWidth * settings.eyeGap
                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Ltr
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            FloatingWindowsLayer(
                                windows = floatingWindows,
                                handControl = handControl,
                                handTrackingEnabled = handTrackingEnabled,
                                sourceWidth = cameraBitmap?.width ?: 640,
                                sourceHeight = cameraBitmap?.height ?: 480,
                                onMove = moveFloatingWindow,
                                onClose = closeFloatingWindow,
                                interactive = true,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            FloatingWindowsLayer(
                                windows = floatingWindows,
                                handControl = handControl,
                                handTrackingEnabled = handTrackingEnabled,
                                sourceWidth = cameraBitmap?.width ?: 640,
                                sourceHeight = cameraBitmap?.height ?: 480,
                                onMove = moveFloatingWindow,
                                onClose = closeFloatingWindow,
                                interactive = false,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            } else {
                FloatingWindowsLayer(
                    windows = floatingWindows,
                    handControl = handControl,
                    handTrackingEnabled = handTrackingEnabled,
                    sourceWidth = cameraBitmap?.width ?: 640,
                    sourceHeight = cameraBitmap?.height ?: 480,
                    onMove = moveFloatingWindow,
                    onClose = closeFloatingWindow,
                    interactive = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (vrMode && handTrackingEnabled) {
            Text(
                text = handError ?: when {
                    handControl?.isPinching == true -> "HAND: GRAB"
                    handControl?.isOpenPalm == true -> "HAND: OPEN"
                    handControl != null -> "HAND: TRACKED"
                    else -> "HAND: SEARCHING"
                },
                color = Color(0xFF8CEBFF),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color(0x99061320), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }

        // Settings remain the topmost layer and all calibration values are unchanged.
        if (showSettings) {
            VrSettingsPanel(
                settings = settings,
                onChange = updateSettings,
                onReset = { updateSettings(VrSettings()) },
                onClose = { showSettings = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .heightIn(max = if (vrMode) 360.dp else 540.dp)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// CameraX source + optional MediaPipe hand detection
// -----------------------------------------------------------------------------

@Composable
private fun CameraFrameSource(
    lifecycleOwner: LifecycleOwner,
    targetRotation: Int,
    handTrackingEnabled: Boolean,
    onFrame: (Bitmap) -> Unit,
    onHandControl: (HandControl?) -> Unit,
    onCameraError: (String) -> Unit,
    onHandError: (String) -> Unit
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val frameCallback by rememberUpdatedState(onFrame)
    val handCallback by rememberUpdatedState(onHandControl)
    val cameraErrorCallback by rememberUpdatedState(onCameraError)
    val handErrorCallback by rememberUpdatedState(onHandError)
    val handEnabledState = rememberUpdatedState(handTrackingEnabled)

    DisposableEffect(lifecycleOwner, targetRotation) {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val handExecutor = Executors.newSingleThreadExecutor()
        val disposed = AtomicBoolean(false)
        val handInitStarted = AtomicBoolean(false)
        val handInferenceBusy = AtomicBoolean(false)
        val handLandmarkerRef = AtomicReference<HandLandmarker?>(null)
        val lastHandTimestamp = AtomicLong(0L)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null
        var boundAnalysis: ImageAnalysis? = null

        cameraProviderFuture.addListener(
            {
                if (!disposed.get()) {
                    try {
                        val provider = cameraProviderFuture.get()
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(640, 480))
                            .setTargetRotation(targetRotation)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        var lastConvertedAt = 0L

                        analysis.setAnalyzer(cameraExecutor) { image ->
                            try {
                                val now = System.nanoTime()
                                // Keep the current camera conversion cadence unchanged here.
                                if (now - lastConvertedAt >= 33_333_333L) {
                                    lastConvertedAt = now
                                    val bitmap = imageProxyToBitmap(image)

                                    mainExecutor.execute {
                                        if (!disposed.get()) {
                                            frameCallback(bitmap)
                                        }
                                    }

                                    if (handEnabledState.value && !disposed.get()) {
                                        // Initialize MediaPipe lazily, only when the user enables hand control.
                                        if (handLandmarkerRef.get() == null &&
                                            handInitStarted.compareAndSet(false, true)
                                        ) {
                                            try {
                                                handExecutor.execute {
                                                    try {
                                                        val baseOptions = BaseOptions.builder()
                                                            .setModelAssetPath("hand_landmarker.task")
                                                            .build()
                                                        val options = HandLandmarker.HandLandmarkerOptions.builder()
                                                            .setBaseOptions(baseOptions)
                                                            .setRunningMode(RunningMode.VIDEO)
                                                            .setNumHands(1)
                                                            .setMinHandDetectionConfidence(0.55f)
                                                            .setMinHandPresenceConfidence(0.55f)
                                                            .setMinTrackingConfidence(0.50f)
                                                            .build()
                                                        val detector = HandLandmarker.createFromOptions(context, options)
                                                        if (disposed.get()) {
                                                            detector.close()
                                                        } else {
                                                            handLandmarkerRef.set(detector)
                                                        }
                                                    } catch (exception: Exception) {
                                                        mainExecutor.execute {
                                                            if (!disposed.get() && handEnabledState.value) {
                                                                handErrorCallback(
                                                                    "تعذر تشغيل تتبع اليد. تأكد من وجود hand_landmarker.task داخل assets. " +
                                                                        (exception.localizedMessage ?: "")
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (exception: RuntimeException) {
                                                mainExecutor.execute {
                                                    if (!disposed.get()) {
                                                        handErrorCallback(exception.localizedMessage ?: "تعذر بدء تتبع اليد.")
                                                    }
                                                }
                                            }
                                        }

                                        val detector = handLandmarkerRef.get()
                                        if (detector != null && handInferenceBusy.compareAndSet(false, true)) {
                                            try {
                                                handExecutor.execute {
                                                    try {
                                                        val timestampNow = SystemClock.uptimeMillis()
                                                        val previous = lastHandTimestamp.get()
                                                        val timestamp = maxOf(timestampNow, previous + 1L)
                                                        lastHandTimestamp.set(timestamp)

                                                        val mpImage = BitmapImageBuilder(bitmap).build()
                                                        val result = detector.detectForVideo(mpImage, timestamp)
                                                        val landmarks = result.landmarks().firstOrNull()
                                                        val hand = if (landmarks != null && landmarks.size >= 21) {
                                                            val wrist = landmarks[0]
                                                            val thumbTip = landmarks[4]
                                                            val indexTip = landmarks[8]

                                                            val pinchDistance = distance2d(
                                                                thumbTip.x(), thumbTip.y(),
                                                                indexTip.x(), indexTip.y()
                                                            )
                                                            val pinching = pinchDistance < 0.065f

                                                            fun fingerExtended(tipIndex: Int, pipIndex: Int): Boolean {
                                                                val tip = landmarks[tipIndex]
                                                                val pip = landmarks[pipIndex]
                                                                val tipDistance = distance2d(
                                                                    tip.x(), tip.y(), wrist.x(), wrist.y()
                                                                )
                                                                val pipDistance = distance2d(
                                                                    pip.x(), pip.y(), wrist.x(), wrist.y()
                                                                )
                                                                return tipDistance > pipDistance * 1.16f
                                                            }

                                                            val extendedCount = listOf(
                                                                fingerExtended(8, 6),
                                                                fingerExtended(12, 10),
                                                                fingerExtended(16, 14),
                                                                fingerExtended(20, 18)
                                                            ).count { it }

                                                            HandControl(
                                                                x = indexTip.x().coerceIn(0f, 1f),
                                                                y = indexTip.y().coerceIn(0f, 1f),
                                                                isPinching = pinching,
                                                                isOpenPalm = extendedCount >= 3 && !pinching,
                                                                timestampMs = timestamp
                                                            )
                                                        } else {
                                                            null
                                                        }

                                                        mainExecutor.execute {
                                                            if (!disposed.get() && handEnabledState.value) {
                                                                handCallback(hand)
                                                            }
                                                        }
                                                    } catch (exception: Exception) {
                                                        mainExecutor.execute {
                                                            if (!disposed.get() && handEnabledState.value) {
                                                                handErrorCallback(
                                                                    exception.localizedMessage ?: "حدث خطأ أثناء تحليل اليد."
                                                                )
                                                            }
                                                        }
                                                    } finally {
                                                        handInferenceBusy.set(false)
                                                    }
                                                }
                                            } catch (exception: RuntimeException) {
                                                handInferenceBusy.set(false)
                                            }
                                        }
                                    }
                                }
                            } catch (exception: Exception) {
                                mainExecutor.execute {
                                    if (!disposed.get()) {
                                        cameraErrorCallback(
                                            exception.localizedMessage ?: "تعذر معالجة صورة الكاميرا."
                                        )
                                    }
                                }
                            } finally {
                                image.close()
                            }
                        }

                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            analysis
                        )

                        boundProvider = provider
                        boundAnalysis = analysis
                    } catch (exception: Exception) {
                        cameraErrorCallback(
                            exception.localizedMessage ?: "تعذر تشغيل الكاميرا الخلفية."
                        )
                    }
                }
            },
            mainExecutor
        )

        onDispose {
            disposed.set(true)
            handCallback(null)

            val provider = boundProvider
            val analysis = boundAnalysis
            if (provider != null && analysis != null) {
                try {
                    provider.unbind(analysis)
                } catch (_: Exception) {
                    // Ignore teardown races.
                }
            }

            // Queue close after any in-progress inference on the same serial executor.
            try {
                handExecutor.execute {
                    handLandmarkerRef.getAndSet(null)?.close()
                }
            } catch (_: RuntimeException) {
                // The executor may already be shutting down.
            }
            handExecutor.shutdown()
            cameraExecutor.shutdown()
        }
    }
}

// -----------------------------------------------------------------------------
// Convert CameraX YUV frame to ARGB bitmap (existing camera path preserved)
// -----------------------------------------------------------------------------

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val width = image.width
    val height = image.height
    val planes = image.planes

    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer.duplicate()
    val uBuffer = uPlane.buffer.duplicate()
    val vBuffer = vPlane.buffer.duplicate()

    val yStart = yBuffer.position()
    val uStart = uBuffer.position()
    val vStart = vBuffer.position()
    val output = IntArray(width * height)

    for (row in 0 until height) {
        for (col in 0 until width) {
            val yIndex = yStart + row * yPlane.rowStride + col * yPlane.pixelStride
            val uIndex = uStart + (row / 2) * uPlane.rowStride + (col / 2) * uPlane.pixelStride
            val vIndex = vStart + (row / 2) * vPlane.rowStride + (col / 2) * vPlane.pixelStride

            val y = (yBuffer.get(yIndex).toInt() and 0xFF) - 16
            val u = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
            val v = (vBuffer.get(vIndex).toInt() and 0xFF) - 128
            val c = y.coerceAtLeast(0)

            val red = ((298 * c + 409 * v + 128) shr 8).coerceIn(0, 255)
            val green = ((298 * c - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
            val blue = ((298 * c + 516 * u + 128) shr 8).coerceIn(0, 255)
            output[row * width + col] = android.graphics.Color.rgb(red, green, blue)
        }
    }

    val bitmap = Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    val rotation = image.imageInfo.rotationDegrees % 360
    if (rotation == 0) return bitmap

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
    )
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

// -----------------------------------------------------------------------------
// Camera image viewport
// -----------------------------------------------------------------------------

@Composable
private fun EyeViewport(
    bitmap: Bitmap?,
    settings: VrSettings,
    eyeScale: Float,
    offsetX: Float,
    offsetY: Float,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    val colorFilter = remember(settings.brightness, settings.contrast, settings.saturation) {
        createCameraColorFilter(
            brightness = settings.brightness,
            contrast = settings.contrast,
            saturation = settings.saturation
        )
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black)
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Camera image",
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val mirrorFactor = if (settings.mirror) -1f else 1f
                        scaleX = settings.zoom * eyeScale * mirrorFactor
                        scaleY = settings.zoom * eyeScale
                        translationX = offsetX * size.width
                        translationY = offsetY * size.height
                    }
            )
        } else {
            Text(
                text = "في انتظار الكاميرا",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (settings.vignette > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = settings.vignette)
                            ),
                            radius = 800f
                        )
                    )
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Floating windows layer: touch drag + hand pinch-and-drag
// -----------------------------------------------------------------------------

private fun mapCameraPointToCroppedViewport(
    x: Float,
    y: Float,
    sourceAspect: Float,
    viewportAspect: Float
): Pair<Float, Float> {
    if (sourceAspect <= 0f || viewportAspect <= 0f) return x to y

    return if (sourceAspect > viewportAspect) {
        val visibleWidth = (viewportAspect / sourceAspect).coerceIn(0.01f, 1f)
        val leftCrop = (1f - visibleWidth) / 2f
        ((x - leftCrop) / visibleWidth).coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
    } else {
        val visibleHeight = (sourceAspect / viewportAspect).coerceIn(0.01f, 1f)
        val topCrop = (1f - visibleHeight) / 2f
        x.coerceIn(0f, 1f) to ((y - topCrop) / visibleHeight).coerceIn(0f, 1f)
    }
}

@Composable
private fun FloatingWindowsLayer(
    windows: List<SpatialWindow>,
    handControl: HandControl?,
    handTrackingEnabled: Boolean,
    sourceWidth: Int,
    sourceHeight: Int,
    onMove: (Int, Float, Float, Float, Float) -> Unit,
    onClose: (Int) -> Unit,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val layerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val layerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val panelWidthPx = with(density) { 220.dp.toPx() }
        val panelHeightPx = with(density) { 150.dp.toPx() }
        val maxX = ((layerWidthPx - panelWidthPx) / layerWidthPx).coerceIn(0f, 1f)
        val maxY = ((layerHeightPx - panelHeightPx) / layerHeightPx).coerceIn(0f, 1f)
        val sourceAspect = sourceWidth.toFloat() / sourceHeight.coerceAtLeast(1).toFloat()
        val viewportAspect = layerWidthPx / layerHeightPx

        val mappedHandPoint = handControl?.let {
            mapCameraPointToCroppedViewport(
                it.x,
                it.y,
                sourceAspect,
                viewportAspect
            )
        }

        var handGrabbedWindowId by remember { mutableStateOf<Int?>(null) }
        var previousHandPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }

        LaunchedEffect(
            handControl?.timestampMs,
            handTrackingEnabled,
            interactive
        ) {
            if (!interactive || !handTrackingEnabled || handControl == null || mappedHandPoint == null) {
                handGrabbedWindowId = null
                previousHandPoint = null
            } else {
                val point = mappedHandPoint
                if (handControl.isPinching) {
                    val grabbedId = handGrabbedWindowId
                    if (grabbedId == null) {
                        val windowWidthNorm = panelWidthPx / layerWidthPx
                        val windowHeightNorm = panelHeightPx / layerHeightPx
                        val hit = windows.lastOrNull { window ->
                            point.first >= window.x &&
                                point.first <= window.x + windowWidthNorm &&
                                point.second >= window.y &&
                                point.second <= window.y + windowHeightNorm
                        }
                        handGrabbedWindowId = hit?.id
                        previousHandPoint = if (hit != null) point else null
                    } else {
                        val previous = previousHandPoint
                        if (previous != null) {
                            onMove(
                                grabbedId,
                                point.first - previous.first,
                                point.second - previous.second,
                                maxX,
                                maxY
                            )
                        }
                        previousHandPoint = point
                    }
                } else {
                    handGrabbedWindowId = null
                    previousHandPoint = null
                }
            }
        }

        windows.forEach { panel ->
            Surface(
                modifier = Modifier
                    .offset(
                        x = maxWidth * panel.x,
                        y = maxHeight * panel.y
                    )
                    .width(220.dp)
                    .border(
                        width = 1.dp,
                        color = Color(0x9955DFFF),
                        shape = RoundedCornerShape(18.dp)
                    ),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xB8172D45),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = panel.title,
                            color = Color(0xFF66E4FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(panel.id, interactive, layerWidthPx, layerHeightPx) {
                                    if (interactive) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            onMove(
                                                panel.id,
                                                dragAmount.x / layerWidthPx,
                                                dragAmount.y / layerHeightPx,
                                                maxX,
                                                maxY
                                            )
                                        }
                                    }
                                }
                        )
                        TextButton(
                            enabled = interactive,
                            onClick = { onClose(panel.id) }
                        ) {
                            Text("×", color = Color.White, fontSize = 20.sp)
                        }
                    }
                    Text(
                        text = panel.message,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (interactive) "اسحب العنوان للتحريك" else "SPATIAL WINDOW",
                        color = Color(0xFF9AB4C9),
                        fontSize = 9.sp
                    )
                }
            }
        }

        if (handTrackingEnabled && handControl != null && mappedHandPoint != null) {
            val point = mappedHandPoint
            Box(
                modifier = Modifier
                    .offset(
                        x = maxWidth * point.first - 10.dp,
                        y = maxHeight * point.second - 10.dp
                    )
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (handControl.isPinching) Color(0xFFFFC857)
                        else Color(0xFF55E8FF)
                    )
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Brightness / contrast / saturation filter
// -----------------------------------------------------------------------------

private fun createCameraColorFilter(
    brightness: Float,
    contrast: Float,
    saturation: Float
): ColorFilter {
    val inverse = 1f - saturation
    val redR = 0.213f * inverse + saturation
    val redG = 0.715f * inverse
    val redB = 0.072f * inverse
    val greenR = 0.213f * inverse
    val greenG = 0.715f * inverse + saturation
    val greenB = 0.072f * inverse
    val blueR = 0.213f * inverse
    val blueG = 0.715f * inverse
    val blueB = 0.072f * inverse + saturation
    val offset = (1f - contrast) * 127.5f + brightness * 255f

    val matrix = ColorMatrix(
        floatArrayOf(
            contrast * redR, contrast * redG, contrast * redB, 0f, offset,
            contrast * greenR, contrast * greenG, contrast * greenB, 0f, offset,
            contrast * blueR, contrast * blueG, contrast * blueB, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return ColorFilter.colorMatrix(matrix)
}

// -----------------------------------------------------------------------------
// VR settings panel (all previous controls retained)
// -----------------------------------------------------------------------------

@Composable
private fun VrSettingsPanel(
    settings: VrSettings,
    onChange: (VrSettings) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color(0xF20A1728)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "VR CONTROL CENTER",
                    color = Color(0xFF55DFFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Button(onClick = onClose) { Text("تم") }
            }

            Text("إعدادات تقسيم الشاشة", color = Color.White, fontWeight = FontWeight.Bold)
            SettingSlider(
                title = "الفاصل بين الصورتين",
                value = settings.eyeGap,
                valueText = "${(settings.eyeGap * 100).toInt()}%",
                range = 0f..0.14f,
                onValueChange = { onChange(settings.copy(eyeGap = it)) }
            )
            SettingSlider(
                title = "التكبير العام للصورتين",
                value = settings.zoom,
                valueText = String.format(Locale.US, "%.2fx", settings.zoom),
                range = 0.65f..1.60f,
                onValueChange = { onChange(settings.copy(zoom = it)) }
            )

            Text("العين اليسرى", color = Color(0xFF55DFFF), fontWeight = FontWeight.Bold)
            SettingSlider(
                title = "حجم الصورة اليسرى",
                value = settings.leftScale,
                valueText = String.format(Locale.US, "%.2f", settings.leftScale),
                range = 0.70f..1.40f,
                onValueChange = { onChange(settings.copy(leftScale = it)) }
            )
            SettingSlider(
                title = "موضع الصورة اليسرى أفقيًا",
                value = settings.leftX,
                valueText = "${(settings.leftX * 100).toInt()}%",
                range = -0.30f..0.30f,
                onValueChange = { onChange(settings.copy(leftX = it)) }
            )
            SettingSlider(
                title = "موضع الصورة اليسرى رأسيًا",
                value = settings.leftY,
                valueText = "${(settings.leftY * 100).toInt()}%",
                range = -0.30f..0.30f,
                onValueChange = { onChange(settings.copy(leftY = it)) }
            )

            Text("العين اليمنى", color = Color(0xFF55DFFF), fontWeight = FontWeight.Bold)
            SettingSlider(
                title = "حجم الصورة اليمنى",
                value = settings.rightScale,
                valueText = String.format(Locale.US, "%.2f", settings.rightScale),
                range = 0.70f..1.40f,
                onValueChange = { onChange(settings.copy(rightScale = it)) }
            )
            SettingSlider(
                title = "موضع الصورة اليمنى أفقيًا",
                value = settings.rightX,
                valueText = "${(settings.rightX * 100).toInt()}%",
                range = -0.30f..0.30f,
                onValueChange = { onChange(settings.copy(rightX = it)) }
            )
            SettingSlider(
                title = "موضع الصورة اليمنى رأسيًا",
                value = settings.rightY,
                valueText = "${(settings.rightY * 100).toInt()}%",
                range = -0.30f..0.30f,
                onValueChange = { onChange(settings.copy(rightY = it)) }
            )

            Text("تحسين الصورة", color = Color(0xFF55DFFF), fontWeight = FontWeight.Bold)
            SettingSlider(
                title = "السطوع",
                value = settings.brightness,
                valueText = String.format(Locale.US, "%.2f", settings.brightness),
                range = -0.30f..0.30f,
                onValueChange = { onChange(settings.copy(brightness = it)) }
            )
            SettingSlider(
                title = "التباين",
                value = settings.contrast,
                valueText = String.format(Locale.US, "%.2f", settings.contrast),
                range = 0.50f..1.50f,
                onValueChange = { onChange(settings.copy(contrast = it)) }
            )
            SettingSlider(
                title = "تشبع الألوان",
                value = settings.saturation,
                valueText = String.format(Locale.US, "%.2f", settings.saturation),
                range = 0f..2f,
                onValueChange = { onChange(settings.copy(saturation = it)) }
            )
            SettingSlider(
                title = "تعتيم أطراف الصورة",
                value = settings.vignette,
                valueText = String.format(Locale.US, "%.2f", settings.vignette),
                range = 0f..0.70f,
                onValueChange = { onChange(settings.copy(vignette = it)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("عكس الصورة", color = Color.White)
                    Text("تبديل اتجاه المشهد أفقيًا", color = Color(0xFFB9C9D9), fontSize = 12.sp)
                }
                Switch(
                    checked = settings.mirror,
                    onCheckedChange = { onChange(settings.copy(mirror = it)) }
                )
            }

            Text("تُحفظ الإعدادات تلقائيًا على الهاتف.", color = Color(0xFFB9C9D9), fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = onReset) { Text("استعادة الافتراضي") }
                Button(onClick = onClose) { Text("حفظ وإغلاق") }
            }
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontSize = 13.sp)
            Text(valueText, color = Color(0xFF55DFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
