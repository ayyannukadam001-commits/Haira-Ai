package com.haira.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HairaHome() }
    }
}

@Composable
fun HairaHome() {
    val background = Color(0xFF050A12)
    val cyan = Color(0xFF35D9F2)
    MaterialTheme(colorScheme = darkColorScheme(
        background = background,
        surface = Color(0xFF101C2A),
        primary = cyan
    )) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF10283A), background)))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("HAIRA", color = cyan, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text("PERSONAL AI ASSISTANT", color = Color.LightGray, fontSize = 12.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(32.dp))
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF102333),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hello, Sir ji", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "HAIRA foundation is running.",
                        color = Color(0xFFB8CBD8),
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Core AI, voice, memory and Android skills are not connected yet.",
                        color = Color(0xFF8FA9BA),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("FOUNDATION BUILD  •  v0.1.0", color = cyan, fontSize = 11.sp)
        }
    }
}
