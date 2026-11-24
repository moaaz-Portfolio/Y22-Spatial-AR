package com.y22.spatialar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    SpatialHome()
                }
            }
        }
    }
}

@Composable
fun SpatialHome() {
    val cyan = Color(0xFF55DFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF071426), Color(0xFF101F38), Color(0xFF030A14))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Y22 SPATIAL AR",
                color = cyan,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "مرحباً بك في عالمك المكاني",
                color = Color.White,
                fontSize = 20.sp
            )

            Spacer(Modifier.height(32.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xCC142B46)
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text("لوحة التحكم الرئيسية", color = cyan, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "دي أول نسخة تجريبية من المشروع.",
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "هنضيف الكاميرا وتتبع الحركة والنوافذ العائمة في المراحل القادمة.",
                        color = Color(0xFFB9C9D9)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(onClick = { }) {
                Text("النظام جاهز للتطوير")
            }
        }
    }
}
