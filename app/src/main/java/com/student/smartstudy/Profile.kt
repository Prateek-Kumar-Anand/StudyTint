package com.student.smartstudy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileField(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ProfileCard(pf: Profile, onEdit: () -> Unit) {
    val initials = pf.name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onEdit), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF5B5BD6), Color(0xFFE0568B)))),
                    contentAlignment = Alignment.Center
                ) { Text(initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pf.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pf.course, fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Edit", color = Accent, fontSize = 13.sp)
            }
            HorizontalDivider(color = Color(0xFFEDEDF2))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileField("Father", pf.father, Modifier.weight(1.2f))
                ProfileField("LURN No.", pf.lurn, Modifier.weight(1f))
                ProfileField("Roll No.", pf.roll, Modifier.weight(0.8f))
            }
        }
    }
}

@Composable
private fun FormField(label: String, value: String, caps: KeyboardCapitalization, last: Boolean, onDone: () -> Unit, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(60)) }, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(capitalization = caps, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, focusedLabelColor = Accent, cursorColor = Accent,
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
        )
    )
}

@Composable
fun SetupScreen(p: Progress, edit: Boolean, onDone: () -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf(p.profile.name) }
    var father by remember { mutableStateOf(p.profile.father) }
    var lurn by remember { mutableStateOf(p.profile.lurn) }
    var roll by remember { mutableStateOf(p.profile.roll) }
    var course by remember { mutableStateOf(p.profile.course) }
    val ok = listOf(name, father, lurn, roll, course).all { it.isNotBlank() }
    fun save() {
        if (!ok) return
        p.saveProfile(Profile(name, father, lurn, roll, course))
        onDone()
    }
    Column(Modifier.fillMaxSize()) {
        if (edit) TopBar("Your details", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = if (edit) 8.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!edit) {
                Text("Let's set you up", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Ink)
                Text("These details stay on this device and appear at the top of your home screen.", color = Color.Gray, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(4.dp))
            }
            FormField("Full name", name, KeyboardCapitalization.Words, false, ::save) { name = it }
            FormField("Father's name", father, KeyboardCapitalization.Words, false, ::save) { father = it }
            FormField("LURN No.", lurn, KeyboardCapitalization.Characters, false, ::save) { lurn = it }
            FormField("Roll No.", roll, KeyboardCapitalization.Characters, false, ::save) { roll = it }
            FormField("Course", course, KeyboardCapitalization.Words, true, ::save) { course = it }
        }
        Button(
            ::save, Modifier.fillMaxWidth().padding(20.dp).height(56.dp), enabled = ok, shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) { Text(if (edit) "Save changes" else "Save and continue", fontSize = 16.sp) }
    }
}
