package com.student.smartstudy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The profile picture: picked from the gallery, cropped square and kept in the app's private storage. */
object ProfilePhoto {
    private const val SIZE = 512

    private fun file(ctx: Context) = File(ctx.filesDir, "profile_photo.jpg")

    fun load(ctx: Context): ImageBitmap? = try {
        val f = file(ctx)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
    } catch (e: Throwable) { null }

    fun write(ctx: Context, bmp: Bitmap) {
        val tmp = File(ctx.filesDir, "profile_photo.tmp")
        FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!tmp.renameTo(file(ctx))) { file(ctx).delete(); tmp.renameTo(file(ctx)) }
    }

    fun delete(ctx: Context) { file(ctx).delete() }

    private fun rotationOf(ctx: Context, uri: Uri): Int = try {
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            when (ExifInterface(ins).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Throwable) { 0 }

    /** Reads the chosen image, fixes its rotation and returns a centred square of [SIZE] px (or null if unreadable). */
    fun decode(ctx: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (raw == null) {
            null
        } else {
            val side = minOf(raw.width, raw.height)
            val m = Matrix()
            m.postRotate(rotationOf(ctx, uri).toFloat())
            m.postScale(SIZE.toFloat() / side, SIZE.toFloat() / side)
            Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side, m, true)
        }
    } catch (e: Throwable) { null }
}

@Composable
fun ProfileField(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ProfileCard(pf: Profile, photo: ImageBitmap?, onEdit: () -> Unit) {
    val initials = pf.name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    Card(
        Modifier.fillMaxWidth().tap(RoundedCornerShape(24.dp), onEdit), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photo != null) {
                    Image(
                        bitmap = photo, contentDescription = "Profile photo", contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF5B5BD6), Color(0xFFE0568B)))),
                        contentAlignment = Alignment.Center
                    ) { Text(initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
                }
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
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // the picked photo is only written to storage when "Save" is pressed
    var pending by remember { mutableStateOf<Bitmap?>(null) }
    var removed by remember { mutableStateOf(false) }
    var photoNote by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { ProfilePhoto.decode(ctx, uri) }
                if (bmp != null) { pending = bmp; removed = false; photoNote = "" } else photoNote = "Could not read that picture."
            }
        }
    }
    val shown: ImageBitmap? = when {
        pending != null -> pending!!.asImageBitmap()
        removed -> null
        else -> p.photo
    }
    fun save() {
        if (!ok) return
        p.saveProfile(Profile(name, father, lurn, roll, course))
        val staged = pending
        if (staged != null) p.savePhoto(staged) else if (removed) p.savePhoto(null)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF5B5BD6), Color(0xFFE0568B))))
                        .clickable { picker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (shown != null) {
                        Image(bitmap = shown, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        val initials = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
                        Text(initials.ifEmpty { "+" }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
                    }
                }
                Column {
                    Text("Profile photo", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Row {
                        TextButton(onClick = { picker.launch("image/*") }) { Text(if (shown == null) "Add photo" else "Change", color = Accent) }
                        if (shown != null) TextButton(onClick = { pending = null; removed = true }) { Text("Remove", color = Color.Gray) }
                    }
                    Text(if (photoNote.isNotEmpty()) photoNote else "Saved on this device", fontSize = 12.sp, color = Color.Gray)
                }
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
