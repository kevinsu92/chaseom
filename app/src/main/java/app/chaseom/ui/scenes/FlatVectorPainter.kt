package app.chaseom.ui.scenes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath

/**
 * Draws an [ImageVector] straight onto the canvas, with no per-painter cache bitmap.
 *
 * Compose's `rememberVectorPainter` keeps one offscreen bitmap per painter at the drawn size. The island
 * draws about 20 full-width layers, so those caches alone cost ~3 MB each on a 2560-wide screen (QA B-4:
 * PSS +76 MB). Here every path is converted once (group transforms baked in) and drawn directly; the
 * alpha passed to [draw] is folded into each path's own alpha, so no layer is allocated either.
 * Supports what the 04_design island set uses: groups (translate/scale/rotate around a pivot), fill and
 * stroke brushes with their alphas, stroke width/cap/join/miter. Clip paths and trim are not used there.
 */
class FlatVectorPainter(private val vector: ImageVector) : Painter() {

    private class Op(
        val path: Path,
        val fill: Brush?, val fillAlpha: Float,
        val stroke: Brush?, val strokeAlpha: Float, val style: Stroke?,
    )

    private val ops: List<Op> = buildList { flatten(vector.root, Matrix(), this) }
    private var alpha = 1f

    override val intrinsicSize: Size = Size(vector.viewportWidth, vector.viewportHeight)

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun DrawScope.onDraw() {
        scale(size.width / vector.viewportWidth, size.height / vector.viewportHeight, pivot = Offset.Zero) {
            for (op in ops) {
                if (op.fill != null && op.fillAlpha > 0f) drawPath(op.path, op.fill, alpha = op.fillAlpha * alpha)
                if (op.stroke != null && op.style != null && op.strokeAlpha > 0f) {
                    drawPath(op.path, op.stroke, alpha = op.strokeAlpha * alpha, style = op.style)
                }
            }
        }
    }

    private fun flatten(group: VectorGroup, parent: Matrix, out: MutableList<Op>) {
        val m = Matrix(parent.values.copyOf())
        val local = Matrix().apply {
            translate(group.pivotX + group.translationX, group.pivotY + group.translationY)
            rotateZ(group.rotation)
            scale(group.scaleX, group.scaleY)
            translate(-group.pivotX, -group.pivotY)
        }
        m.timesAssign(local)
        for (i in 0 until group.size) {
            when (val node = group[i]) {
                is VectorGroup -> flatten(node, m, out)
                is VectorPath -> {
                    val p = node.pathData.toPath().apply { fillType = node.pathFillType; transform(m) }
                    val style = if (node.stroke != null && node.strokeLineWidth > 0f) {
                        Stroke(node.strokeLineWidth, node.strokeLineMiter, node.strokeLineCap, node.strokeLineJoin)
                    } else {
                        null
                    }
                    out += Op(p, node.fill, node.fillAlpha, node.stroke, node.strokeAlpha, style)
                }
            }
        }
    }
}
