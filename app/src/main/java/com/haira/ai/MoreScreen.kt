package com.haira.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoreScreen(
    onModels: () -> Unit,
    onGitHub: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text("CONTROL CENTER", color = Color(0xFF55E6F2), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("Manage HAIRA's models, connected developer tools and preferences.", color = Color(0xFF8CA4B8), fontSize = 10.sp)
        MoreDestination(
            title = "MODEL CENTER",
            detail = "Local model status, model setup and supported model management.",
            symbol = "⬡",
            onClick = onModels
        )
        MoreDestination(
            title = "GITHUB MANAGER",
            detail = "Repositories, source files and GitHub Actions workflows.",
            symbol = "⌘",
            onClick = onGitHub
        )
        MoreDestination(
            title = "SETTINGS & PRIVACY",
            detail = "Voice preferences, local data controls and privacy information.",
            symbol = "⚙",
            onClick = onSettings
        )
        Surface(
            color = Color(0xFF0D1826),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("HONEST STATUS", color = Color(0xFF55E6F2), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Only working features are presented as available. Model compatibility, external access and unfinished capabilities will be labelled clearly.",
                    color = Color(0xFF8CA4B8), fontSize = 9.sp, lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun MoreDestination(title: String, detail: String, symbol: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF0D1826),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = Color(0xFF123448), shape = RoundedCornerShape(11.dp)) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text(symbol, color = Color(0xFF55E6F2), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                Text(detail, color = Color(0xFF8CA4B8), fontSize = 9.sp, lineHeight = 13.sp)
            }
            Text("›", color = Color(0xFF55E6F2), fontSize = 23.sp)
        }
    }
}
