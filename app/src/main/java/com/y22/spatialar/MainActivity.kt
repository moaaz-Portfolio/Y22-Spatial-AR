
package com.y22.spatialar

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Size
import android.view.Surface
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// --------------------------------------------------
// Activity
// --------------------------------------------------

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SpatialHome(
                    lifecycleOwner = this@MainActivity,
                    onVrModeChange = { enabled ->
                        applyVrMode(enabled)
                    }
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

// --------------------------------------------------
// VR settings data
// --------------------------------------------------

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
    val p = context.getSharedPreferences(
        "y22_vr_settings",
        Context.MODE_PRIVATE
    )

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
    context.getSharedPreferences(
        "y22_vr_settings",
        Context.MODE_PRIVATE
    ).edit()
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

// --------------------------------------------------
// Main screen
// --------------------------------------------------

@Composable
fun SpatialHome(
    lifecycleOwner: LifecycleOwner,
    onVrModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val targetRotation = remember(configuration.orientation) {
        @Suppress("DEPRECATION")
        val wm = context.getSystemService(
            Context.WINDOW_SERVICE
        ) as WindowManager

        @Suppress("DEPRECATION")
        wm.defaultDisplay.rotation
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraEnabled by remember { mutableStateOf(true) }
    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var vrMode by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    var settings by remember {
        mutableStateOf(loadVrSettings(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        cameraError = if (granted) {
            null
        } else {
            "لم يتم السماح باستخدام الكاميرا."
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val updateSettings: (VrSettings) -> Unit = { next ->
        settings = next
        saveVrSettings(context, next)
    }

    if (hasCameraPermission && cameraEnabled) {
        CameraFrameSource(
            lifecycleOwner = lifecycleOwner,
            targetRotation = targetRotation,
            onFrame = { bitmap ->
                cameraBitmap = bitmap
            },
            onCameraError = { message ->
                cameraError = message
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        if (vrMode) {

            // VR: صورتان متجاورتان من نفس إطار الكاميرا
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
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
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        EyeViewport(
                            bitmap = cameraBitmap,
                            settings = settings,
                            eyeScale = settings.rightScale,
                            offsetX = settings.rightX,
                            offsetY = settings.rightY,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            // أزرار وضع النظارة
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        vrMode = false
                        showSettings = false
                        onVrModeChange(false)
                    }
                ) {
                    Text("خروج من VR")
                }

                Button(
                    onClick = {
                        showSettings = !showSettings
                    }
                ) {
                    Text(
                        if (showSettings) {
                            "إخفاء الإعدادات"
                        } else {
                            "إعدادات VR"
                        }
                    )
                }
            }

        } else {

            // الوضع العادي
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
                            if (cameraError != null) {
                                cameraError!!
                            } else if (cameraBitmap != null) {
                                "الكاميرا الخلفية تعمل."
                            } else if (!hasCameraPermission) {
                                "نحتاج إذن الكاميرا لبدء العرض."
                            } else {
                                "جاري تشغيل الكاميرا..."
                            },
                            color = Color.White
                        )

                        Button(
                            onClick = {
                                vrMode = true
                                onVrModeChange(true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تشغيل وضع VR Box")
                        }

                        OutlinedButton(
                            onClick = {
                                showSettings = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("الإعدادات المتقدمة")
                        }

                        OutlinedButton(
                            onClick = {
                                if (!hasCameraPermission) {
                                    permissionLauncher.launch(
                                        Manifest.permission.CAMERA
                                    )
                                } else {
                                    cameraEnabled = !cameraEnabled
                                    cameraBitmap = null
                                    cameraError = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (cameraEnabled) {
                                    "إيقاف الكاميرا"
                                } else {
                                    "تشغيل الكاميرا"
                                }
                            )
                        }

                        if (!hasCameraPermission) {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(
                                        Manifest.permission.CAMERA
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("السماح باستخدام الكاميرا")
                            }
                        }
                    }
                }
            }
        }

        // لوحة الإعدادات تظهر فوق الشاشة سواء في الوضع العادي أو VR
        if (showSettings) {
            VrSettingsPanel(
                settings = settings,
                onChange = updateSettings,
                onReset = {
                    updateSettings(VrSettings())
                },
                onClose = {
                    showSettings = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .heightIn(
                        max = if (vrMode) 360.dp else 540.dp
                    )
            )
        }
    }
}

// --------------------------------------------------
// CameraX frame source
// --------------------------------------------------

@Composable
private fun CameraFrameSource(
    lifecycleOwner: LifecycleOwner,
    targetRotation: Int,
    onFrame: (Bitmap) -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) {
        ContextCompat.getMainExecutor(context)
    }

    val frameCallback by rememberUpdatedState(onFrame)
    val errorCallback by rememberUpdatedState(onCameraError)

    DisposableEffect(lifecycleOwner, targetRotation) {

        val executor = Executors.newSingleThreadExecutor()
        val disposed = AtomicBoolean(false)

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)

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
                            .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                            )
                            .build()

                        var lastConvertedAt = 0L

                        analysis.setAnalyzer(executor) { image ->
                            try {
                                val now = System.nanoTime()

                                // نحد من التحويل إلى نحو 10 إطارات في الثانية
                                if (now - lastConvertedAt >= 100_000_000L) {
                                    lastConvertedAt = now

                                    val bitmap = imageProxyToBitmap(image)

                                    mainExecutor.execute {
                                        if (disposed.get()) {
                                            bitmap.recycle()
                                        } else {
                                            frameCallback(bitmap)
                                        }
                                    }
                                }

                            } catch (exception: Exception) {
                                mainExecutor.execute {
                                    if (!disposed.get()) {
                                        errorCallback(
                                            exception.localizedMessage
                                                ?: "تعذر معالجة صورة الكاميرا."
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
                        errorCallback(
                            exception.localizedMessage
                                ?: "تعذر تشغيل الكاميرا الخلفية."
                        )
                    }
                }
            },
            mainExecutor
        )

        onDispose {
            disposed.set(true)

            val provider = boundProvider
            val analysis = boundAnalysis

            if (provider != null && analysis != null) {
                try {
                    provider.unbind(analysis)
                } catch (_: Exception) {
                    // تجاهل أخطاء الإغلاق
                }
            }

            executor.shutdown()
        }
    }
}

// --------------------------------------------------
// Convert CameraX YUV frame to Bitmap
// --------------------------------------------------

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

            val yIndex = yStart +
                row * yPlane.rowStride +
                col * yPlane.pixelStride

            val uIndex = uStart +
                (row / 2) * uPlane.rowStride +
                (col / 2) * uPlane.pixelStride

            val vIndex = vStart +
                (row / 2) * vPlane.rowStride +
                (col / 2) * vPlane.pixelStride

            val y = (yBuffer.get(yIndex).toInt() and 0xFF) - 16
            val u = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
            val v = (vBuffer.get(vIndex).toInt() and 0xFF) - 128

            val c = y.coerceAtLeast(0)

            val red = ((298 * c + 409 * v + 128) shr 8)
                .coerceIn(0, 255)

            val green = ((298 * c - 100 * u - 208 * v + 128) shr 8)
                .coerceIn(0, 255)

            val blue = ((298 * c + 516 * u + 128) shr 8)
                .coerceIn(0, 255)

            output[row * width + col] =
                android.graphics.Color.rgb(red, green, blue)
        }
    }

    val bitmap = Bitmap.createBitmap(
        output,
        width,
        height,
        Bitmap.Config.ARGB_8888
    )

    val rotation = image.imageInfo.rotationDegrees % 360

    if (rotation == 0) {
        return bitmap
    }

    val matrix = Matrix().apply {
        postRotate(rotation.toFloat())
    }

    val rotated = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )

    if (rotated !== bitmap) {
        bitmap.recycle()
    }

    return rotated
}

// --------------------------------------------------
// Camera image with independent eye controls
// --------------------------------------------------

@Composable
private fun EyeViewport(
    bitmap: Bitmap?,
    settings: VrSettings,
    eyeScale: Float,
    offsetX: Float,
    offsetY: Float,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) {
        bitmap?.asImageBitmap()
    }

    val colorFilter = remember(
        settings.brightness,
        settings.contrast,
        settings.saturation
    ) {
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
                        val mirrorFactor =
                            if (settings.mirror) -1f else 1f

                        scaleX =
                            settings.zoom * eyeScale * mirrorFactor

                        scaleY =
                            settings.zoom * eyeScale

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
                                Color.Black.copy(
                                    alpha = settings.vignette
                                )
                            ),
                            radius = 800f
                        )
                    )
            )
        }
    }
}

// --------------------------------------------------
// Brightness / contrast / saturation filter
// --------------------------------------------------

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

    val offset =
        (1f - contrast) * 127.5f + brightness * 255f

    val matrix = ColorMatrix(
        floatArrayOf(
            contrast * redR,
            contrast * redG,
            contrast * redB,
            0f,
            offset,

            contrast * greenR,
            contrast * greenG,
            contrast * greenB,
            0f,
            offset,

            contrast * blueR,
            contrast * blueG,
            contrast * blueB,
            0f,
            offset,

            0f, 0f, 0f, 1f, 0f
        )
    )

    return ColorFilter.colorMatrix(matrix)
}

// --------------------------------------------------
// Settings panel
// --------------------------------------------------

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

                Button(onClick = onClose) {
                    Text("تم")
                }
            }

            Text(
                "إعدادات تقسيم الشاشة",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            SettingSlider(
                title = "الفاصل بين الصورتين",
                value = settings.eyeGap,
                valueText = "${(settings.eyeGap * 100).toInt()}%",
                range = 0f..0.14f,
                onValueChange = {
                    onChange(settings.copy(eyeGap = it))
                }
            )

            SettingSlider(
                title = "التكبير العام للصورتين",
                value = settings.zoom,
                valueText = String.format(Locale.US, "%.2fx", settings.zoom),
                range = 0.65f..1.60f,
                onValueChange = {
                    onChange(settings.copy(zoom = it))
                }
            )

            Text(
                "العين اليسرى",
                color = Color(0xFF55DFFF),
                fontWeight = FontWeight.Bold
            )

            SettingSlider(
                title = "حجم الصورة اليسرى",
                value = settings.leftScale,
                valueText = String.format(Locale.US, "%.2f", settings.leftScale),
                range = 0.70f..1.40f,
                onValueChange = {
                    onChange(settings.copy(leftScale = it))
                }
            )

            SettingSlider(
                title = "موضع الصورة اليسرى أفقيًا",
                value = settings.leftX,
                valueText = "${(settings.leftX * 100).toInt()}%",
                range = -0.30f..0.30f,
                onValueChange = {
                    onChange(settings.copy(leftX = it))
                }
            )

            SettingSlider(
                title = "موضع الصورة اليسرى رأسيًا",
                value = settings.leftY,
                valueText = "${(settings.leftY * 100).toInt()}%",
                range = -0.30f..0.30f,
                onValueChange = {
                    onChange(settings.copy(leftY = it))
                }
            )

            Text(
                "العين اليمنى",
                color = Color(0xFF55DFFF),
                fontWeight = FontWeight.Bold
            )

            SettingSlider(
                title = "حجم الصورة اليمنى",
                value = settings.rightScale,
                valueText = String.format(Locale.US, "%.2f", settings.rightScale),
                range = 0.70f..1.40f,
                onValueChange = {
                    onChange(settings.copy(rightScale = it))
                }
            )

            SettingSlider(
                title = "موضع الصورة اليمنى أفقيًا",
                value = settings.rightX,
                valueText = "${(settings.rightX * 100).toInt()}%",
                range = -0.30f..0.30f,
                onValueChange = {
                    onChange(settings.copy(rightX = it))
                }
            )

            SettingSlider(
                title = "موضع الصورة اليمنى رأسيًا",
                value = settings.rightY,
                valueText = "${(settings.rightY * 100).toInt()}%",
                range = -0.30f..0.30f,
                onValueChange = {
                    onChange(settings.copy(rightY = it))
                }
            )

            Text(
                "تحسين الصورة",
                color = Color(0xFF55DFFF),
                fontWeight = FontWeight.Bold
            )

            SettingSlider(
                title = "السطوع",
                value = settings.brightness,
                valueText = String.format(Locale.US, "%.2f", settings.brightness),
                range = -0.30f..0.30f,
                onValueChange = {
                    onChange(settings.copy(brightness = it))
                }
            )

            SettingSlider(
                title = "التباين",
                value = settings.contrast,
                valueText = String.format(Locale.US, "%.2f", settings.contrast),
                range = 0.50f..1.50f,
                onValueChange = {
                    onChange(settings.copy(contrast = it))
                }
            )

            SettingSlider(
                title = "تشبع الألوان",
                value = settings.saturation,
                valueText = String.format(Locale.US, "%.2f", settings.saturation),
                range = 0f..2f,
                onValueChange = {
                    onChange(settings.copy(saturation = it))
                }
            )

            SettingSlider(
                title = "تعتيم أطراف الصورة",
                value = settings.vignette,
                valueText = String.format(Locale.US, "%.2f", settings.vignette),
                range = 0f..0.70f,
                onValueChange = {
                    onChange(settings.copy(vignette = it))
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "عكس الصورة",
                        color = Color.White
                    )

                    Text(
                        "تبديل اتجاه المشهد أفقيًا",
                        color = Color(0xFFB9C9D9),
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = settings.mirror,
                    onCheckedChange = {
                        onChange(settings.copy(mirror = it))
                    }
                )
            }

            Text(
                "تُحفظ الإعدادات تلقائيًا على الهاتف.",
                color = Color(0xFFB9C9D9),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = onReset) {
                    Text("استعادة الافتراضي")
                }

                Button(onClick = onClose) {
                    Text("حفظ وإغلاق")
                }
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
            Text(
                title,
                color = Color.White,
                fontSize = 13.sp
            )

            Text(
                valueText,
                color = Color(0xFF55DFFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}
