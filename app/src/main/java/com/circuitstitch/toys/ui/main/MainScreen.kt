package com.circuitstitch.toys.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.circuitstitch.toys.models.Animal
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun MainScreen(onSettings: () -> Unit, vm: MainViewModel = viewModel()) {
    // Hidden parent gate: drawing a square anywhere opens Settings. Replaces the old gear
    // button, which a toddler could tap. onSettings is recreated each recomposition, so the
    // long-lived gesture coroutine reads the latest via rememberUpdatedState.
    val onSettingsNow by rememberUpdatedState(onSettings)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val minSide = 64.dp.toPx()
                awaitEachGesture {
                    // Passive observer — never consumes, so animal taps still play and the grid
                    // still scrolls. The square is read from fixed screen-space coords, so the
                    // grid scrolling under the finger doesn't distort it. Initial pass so we see
                    // the down + every move regardless of what the children do with them.
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val pts = mutableListOf(down.position)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        pts += change.position
                        if (!change.pressed) break
                    }
                    if (isSquare(pts, minSide)) onSettingsNow()
                }
            },
    ) {
        // Columns = how many ~200dp cells fit across, min 2 — so cells stay finger-big. Phones get
        // 2 fat columns; tablets get more. (24 animals, 200dp: ~10in portrait→4, ~10in landscape→6.)
        val columns = (maxWidth / 200.dp).toInt().coerceAtLeast(2)
        val rows = ceil(Animal.entries.size / columns.toFloat()).toInt()
        val cellWidth = maxWidth / columns
        val fitHeight = maxHeight / rows                 // cell height that makes all rows fill the screen
        // If the rows fit (square cells overflow by <15%), set height = fitHeight so they fill top-to-
        // bottom with no white gap or scroll — slight stretch/squish. Big overflow (phones) stays square + scrolls.
        val cellHeight = if (fitHeight >= cellWidth * 0.85f) fitHeight else cellWidth
        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize()) {
            items(Animal.entries) { animal ->
                Image(
                    painter = painterResource(animal.image),
                    contentDescription = animal.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cellHeight)
                        .clickable { vm.play(animal) },
                )
            }
        }
    }
}

/**
 * True if [points] trace a square-like loop — anywhere on screen, any size, any rotation, drawn
 * clockwise or counter-clockwise. Rotation-invariant and robust to sloppy/overshot corners: a
 * closed loop with a roughly 1:1 bounding box (which holds for a square at any rotation) whose
 * edges are "rectilinear" — all aligned to one of two perpendicular axes. [rectilinearity]
 * measures that, cleanly separating squares/rectangles from circles, triangles, and scribbles
 * regardless of orientation.
 */
private fun isSquare(points: List<Offset>, minSide: Float): Boolean {
    if (points.size < 8) return false
    val w = points.maxOf { it.x } - points.minOf { it.x }
    val h = points.maxOf { it.y } - points.minOf { it.y }
    if (w < minSide || h < minSide) return false
    if (w / h < 0.5f || w / h > 2f) return false                                 // bbox stays ~1:1 at any rotation
    if ((points.first() - points.last()).getDistance() > 0.40f * maxOf(w, h)) return false   // closed loop
    return rectilinearity(points) >= 0.60f
}

/**
 * 0..1 measure of how axis-aligned a stroke's edges are, invariant to its overall rotation. Each
 * segment direction is quadrupled — collapsing a square's four 90°-apart edge directions onto a
 * single angle — and length-weighted, so short overshoot/jitter segments barely count; the
 * length of the resulting average unit vector is the concentration. ~1 for a square/rectangle at
 * any angle, ~0 for a circle, ~0.17 for a triangle.
 */
private fun rectilinearity(points: List<Offset>): Float {
    var sx = 0.0
    var sy = 0.0
    var wsum = 0.0
    for (i in 1 until points.size) {
        val dx = (points[i].x - points[i - 1].x).toDouble()
        val dy = (points[i].y - points[i - 1].y).toDouble()
        val len = hypot(dx, dy)
        if (len == 0.0) continue
        val ang = atan2(dy, dx) * 4.0
        sx += len * cos(ang)
        sy += len * sin(ang)
        wsum += len
    }
    return if (wsum == 0.0) 0f else (hypot(sx, sy) / wsum).toFloat()
}
