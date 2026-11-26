package app.mytasks.ui

import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mytasks.core.LIST_COLORS
import app.mytasks.core.SubList
import app.mytasks.core.TaskList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val DELETE_RED = Color(0xFFFF5722)

/**
 * The reference app's name dialog: a title and an underlined name field on
 * the page colour, then a grey panel with extra options and the
 * DELETE / CANCEL / SAVE buttons.
 */
@Composable
private fun PanelDialog(
    title: String,
    name: String,
    onName: (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: () -> Unit,
    autoFocus: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    val panel = if (colors.isDark) Color(0xFF424242) else Color(0xFFEEEEEE)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = colors.background,
            contentColor = colors.text,
            modifier = Modifier.padding(horizontal = 25.dp).fillMaxWidth(),
        ) {
            Column {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 18.dp)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    val focus = rememberKeyboardFocus(enabled = autoFocus)
                    BasicTextField(
                        value = name,
                        onValueChange = onName,
                        singleLine = true,
                        textStyle = TextStyle(color = colors.text, fontSize = 20.sp),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .padding(horizontal = 4.dp)
                            .drawBehind {
                                val y = size.height + 6.dp.toPx()
                                drawLine(colors.checkbox, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Column(Modifier.fillMaxWidth().background(panel)) {
                    content()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onDelete != null) PanelButton("DELETE", DELETE_RED, onDelete)
                        Spacer(Modifier.weight(1f))
                        PanelButton("CANCEL", colors.accent, onDismiss)
                        PanelButton("SAVE", colors.accent, onSave)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelButton(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

/** List Name: the name and a colour wheel with a brightness slider. */
@Composable
fun ListDialog(
    list: TaskList?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (String, Long) -> Unit,
) {
    var name by remember { mutableStateOf(list?.title ?: "") }
    var color by remember { mutableStateOf(list?.color ?: LIST_COLORS[0]) }
    PanelDialog(
        title = "List Name",
        name = name,
        onName = { name = it },
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            if (name.isNotBlank()) onSave(name, color)
            onDismiss()
        },
        autoFocus = list == null,
    ) {
        ColorWheel(color = color, onColor = { color = it }, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp))
    }
}

/** Sub List Name: the name and where its completed tasks go. */
@Composable
fun SubListDialog(
    sub: SubList?,
    others: List<SubList>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (String, String?) -> Unit,
) {
    val colors = appColors()
    var name by remember { mutableStateOf(sub?.title ?: "") }
    var target by remember { mutableStateOf(sub?.completedSubListId?.takeIf { id -> others.any { it.id == id } }) }
    var open by remember { mutableStateOf(false) }
    PanelDialog(
        title = "Sub List Name",
        name = name,
        onName = { name = it },
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            if (name.isNotBlank()) onSave(name, target)
            onDismiss()
        },
        autoFocus = sub == null,
    ) {
        Text(
            "Where should completed tasks go?",
            color = colors.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
        )
        Box {
            Row(
                Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = colors.text)
                Spacer(Modifier.width(24.dp))
                Text(others.firstOrNull { it.id == target }?.title ?: "This list", fontSize = 20.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.textSecondary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("This list") }, onClick = { target = null; open = false })
                others.forEach { other ->
                    DropdownMenuItem(text = { Text(other.title) }, onClick = { target = other.id; open = false })
                }
            }
        }
    }
}

private const val RINGS = 8

/**
 * A wheel of colour dots (hue around, strength outwards) with a brightness
 * slider underneath. Tap a dot to pick it.
 */
@Composable
fun ColorWheel(color: Long, onColor: (Long) -> Unit, modifier: Modifier = Modifier) {
    val start = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toInt(), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val outline = appColors().text

    fun emit() = onColor(Color.hsv(hue, sat, value).toArgb().toLong() and 0xFFFFFFFFL)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val step = (size.width / 2f) / (RINGS - 0.5f)
                        val ring = (hypot(tap.x - center.x, tap.y - center.y) / step + 0.5f).toInt().coerceIn(0, RINGS - 1)
                        if (ring == 0) {
                            sat = 0f
                        } else {
                            val count = ring * 6
                            var angle = Math.toDegrees(kotlin.math.atan2((tap.y - center.y).toDouble(), (tap.x - center.x).toDouble())).toFloat()
                            if (angle < 0) angle += 360f
                            val index = ((angle / 360f) * count + 0.5f).toInt() % count
                            hue = 360f * index / count
                            sat = ring / (RINGS - 1f)
                        }
                        // A colour picked at zero brightness would stay black, so lift it.
                        if (value < 0.05f) value = 1f
                        emit()
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val step = (size.width / 2f) / (RINGS - 0.5f)
            val dot = step * 0.5f
            for (ring in 0 until RINGS) {
                val count = if (ring == 0) 1 else ring * 6
                for (i in 0 until count) {
                    val dotHue = 360f * i / count
                    val dotSat = ring / (RINGS - 1f)
                    val angle = 2 * PI * i / count
                    val at = Offset(center.x + (ring * step * cos(angle)).toFloat(), center.y + (ring * step * sin(angle)).toFloat())
                    drawCircle(Color.hsv(dotHue, dotSat, value), radius = dot, center = at)
                    val selected = if (ring == 0) sat == 0f else (dotSat == sat && dotHue == hue)
                    if (selected) drawCircle(outline, radius = dot, center = at, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
        // Brightness, from black to the chosen colour at full strength.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 4.dp)
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        value = (tap.x / size.width).coerceIn(0f, 1f)
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        value = (change.position.x / size.width).coerceIn(0f, 1f)
                        emit()
                    }
                },
        ) {
            val y = size.height / 2f
            val thumb = 8.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, sat, 1f))),
                start = Offset(thumb, y),
                end = Offset(size.width - thumb, y),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val x = thumb + (size.width - 2 * thumb) * value
            drawCircle(Color.hsv(hue, sat, value), radius = thumb, center = Offset(x, y))
            drawCircle(outline.copy(alpha = 0.6f), radius = thumb, center = Offset(x, y), style = Stroke(width = 1.dp.toPx()))
        }
    }
}
