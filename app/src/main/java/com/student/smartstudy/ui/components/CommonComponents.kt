package com.student.smartstudy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubjectBadge(s: Subject) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(s.color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center) {
        Text(s.mark, color = s.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Orb(size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(
            Brush.linearGradient(listOf(Color(0xFFFBE38A), Color(0xFFE2AA2C), Color(0xFFB27A0B)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(size / 6)) {
            repeat(2) {
                Box(Modifier.size(size / 11, size / 6).clip(RoundedCornerShape(topStart = 50.dp,
                    topEnd = 50.dp)).background(Color.White))
            }
        }
    }
}

@Composable
fun OptionCard(title: String, sub: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.tap(RoundedCornerShape(22.dp), onClick), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, GoldLine)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Box(Modifier.width(30.dp).height(4.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Ink, maxLines = 1)
            Text(sub, color = Color(0xFF8A7B52), fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, GoldLine),
            modifier = Modifier.size(42.dp).tap(CircleShape, onBack)) {
            Box(contentAlignment = Alignment.Center) { Text("←", fontSize = 20.sp, color = Accent,
                fontWeight = FontWeight.Bold) }
        }
        Text(title, Modifier.weight(1f).padding(end = 42.dp), textAlign = TextAlign.Center, fontSize = 18.sp,
            fontWeight = FontWeight.Medium, color = Ink)
    }
}
