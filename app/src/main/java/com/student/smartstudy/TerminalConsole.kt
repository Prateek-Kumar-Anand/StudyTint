package com.student.smartstudy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_SHOWN = 30_000

/**
 * The output box shared by C Lab and Python Lab. While a program runs, a prompt line at the bottom
 * of the box lets you type what the program asks for (scanf / input()) and press Enter, like a terminal.
 */
@Composable
fun ConsoleBox(
    output: AnnotatedString,
    status: String,
    statusOk: Boolean,
    running: Boolean,
    waiting: Boolean,
    onSubmit: (String) -> Unit,
    onEof: () -> Unit
) {
    val mono = FontFamily.Monospace
    Column(
        Modifier.fillMaxWidth().heightIn(min = 120.dp)
            .background(Color(0xFF111118), RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Text("Output", fontSize = 12.sp, color = Color(0xFF8A8A94))
        Spacer(Modifier.padding(top = 6.dp))
        // Laying out a very long text on every update makes the whole screen stutter, so only the last part is drawn.
        val shown = if (output.length > MAX_SHOWN) {
            androidx.compose.ui.text.buildAnnotatedString {
                append("… earlier output hidden …\n")
                append(output.subSequence(output.length - MAX_SHOWN, output.length))
            }
        } else output
        SelectionContainer {
            Text(shown, fontFamily = mono, fontSize = 13.sp, color = Color(0xFFE6E6F0), lineHeight = 19.sp)
        }
        if (running) ConsoleInput(waiting, onSubmit, onEof)
        if (status.isNotEmpty()) {
            Spacer(Modifier.padding(top = 8.dp))
            Text(status, fontFamily = mono, fontSize = 12.sp, color = if (statusOk) Color(0xFF7EE0A8) else Color(0xFFFFB86C))
        }
    }
}

@Composable
private fun ConsoleInput(waiting: Boolean, onSubmit: (String) -> Unit, onEof: () -> Unit) {
    val mono = FontFamily.Monospace
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    // open the keyboard as soon as the program starts waiting for something to be typed
    LaunchedEffect(waiting) {
        if (waiting) {
            try { focus.requestFocus() } catch (e: Exception) { }
        }
    }
    fun send() {
        val line = text
        text = ""
        onSubmit(line)
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp)
            .background(Color(0xFF1B1B26), RoundedCornerShape(10.dp)).padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("›", color = if (waiting) Color(0xFF7EE0A8) else Color(0xFF5A5A66), fontFamily = mono, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            if (text.isEmpty()) {
                Text(
                    if (waiting) "type here and press Enter" else "running…",
                    color = Color(0xFF6B6B78), fontFamily = mono, fontSize = 13.sp
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it.replace("\n", "") },
                singleLine = true,
                textStyle = TextStyle(fontFamily = mono, fontSize = 14.sp, color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
        val link = ButtonDefaults.textButtonColors(contentColor = Color(0xFF9CC2FF))
        TextButton(onClick = { send() }, colors = link) { Text("Send") }
        TextButton(onClick = { onEof() }, colors = link) { Text("EOF") }
    }
}

/** Turns a stream of bytes into text without breaking a multi-byte character that is split between two reads. */
class Utf8Chunker {
    private var carry = ByteArray(0)

    fun feed(bytes: ByteArray): String {
        val all = if (carry.isEmpty()) bytes else carry + bytes
        var end = all.size
        var i = all.size - 1
        var back = 0
        while (i >= 0 && back < 3 && (all[i].toInt() and 0xC0) == 0x80) { i--; back++ }
        if (i >= 0) {
            val lead = all[i].toInt() and 0xFF
            val need = when {
                lead >= 0xF0 -> 4
                lead >= 0xE0 -> 3
                lead >= 0xC0 -> 2
                else -> 1
            }
            if (need > back + 1) end = i
        }
        carry = all.copyOfRange(end, all.size)
        return String(all, 0, end, Charsets.UTF_8)
    }
}
