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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Welcome(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Image(
            painterResource(R.drawable.logo_full), "StudyTint logo",
            Modifier.fillMaxWidth(0.88f), contentScale = ContentScale.FillWidth
        )
        Spacer(Modifier.height(12.dp))
        Text("Your smart study companion", color = GoldLight, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Text("Python  •  Maths  •  C  •  Exploration in AI  •  DBMS", color = Color(0xFFB9A56A),
            textAlign = TextAlign.Center, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onStart, Modifier.fillMaxWidth().height(60.dp), shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = HeroInk)
        ) { Text("Get started  →", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    }
}
