package ir.xilo.app.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens aligned with `openspec/changes/xilo-platform/specs/ui-ux-spec.md` §9.
 */
object XiloMotion {
    object Duration {
        const val Instant = 0
        const val Fast = 150
        const val Normal = 250
        const val Slow = 350
        const val Deliberate = 500
    }

    object Easing {
        val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        val Decelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)
        val Accelerate = CubicBezierEasing(0.4f, 0f, 1f, 1f)
        val Spring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    }

    /** Like/reaction scale — heartBeat 300ms spring curve (ui-ux-spec §9.3). */
    val heartBeat: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> tweenNormal(): AnimationSpec<T> = tween(
        durationMillis = Duration.Normal,
        easing = Easing.Standard,
    )

    fun <T> tweenFast(): AnimationSpec<T> = tween(
        durationMillis = Duration.Fast,
        easing = Easing.Standard,
    )

    fun <T> tweenSlow(): AnimationSpec<T> = tween(
        durationMillis = Duration.Slow,
        easing = Easing.Decelerate,
    )
}
