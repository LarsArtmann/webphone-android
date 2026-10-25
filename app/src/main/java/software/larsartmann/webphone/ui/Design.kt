package software.larsartmann.webphone.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import software.larsartmann.webphone.R

/** dp → px at the current display density. */
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** dp → px, keeping the fraction for shape radii. */
fun Context.dpf(value: Float): Float = value * resources.displayMetrics.density

/** Resolve a design-token color from the theme (day/night safe). */
fun Context.themeColor(attr: Int): Int {
    val typed = TypedValue()
    theme.resolveAttribute(attr, typed, true)
    return typed.data
}

/** The one emphasis face: system medium (no bundled font to load). */
val mediumTypeface: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

/** Wrap a shape in the standard touch ripple; the shape doubles as
 *  the ripple mask so the effect stays inside the silhouette. */
fun Drawable.withRipple(context: Context): Drawable = RippleDrawable(
    ColorStateList.valueOf(context.themeColor(androidx.appcompat.R.attr.colorControlHighlight)),
    this,
    this,
)

/** Fully-rounded pill background (chips, pills, the call button). */
fun Context.pillBackground(color: Int): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = dpf(999f)
    setColor(color)
}

/** Rounded-rectangle background (fields, cards, sheets). */
fun Context.roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = dpf(radiusDp)
    setColor(color)
}

/** Circular background (keys, icon buttons, avatars). */
fun Context.circleBackground(color: Int): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}

/** Shared typography pass: size, color, optional medium weight,
 *  letter-spacing (em) and small-caps-style uppercase for eyebrows. */
fun TextView.styleText(
    sizeSp: Float,
    color: Int,
    medium: Boolean = false,
    letterSpacingEm: Float = 0f,
    allCaps: Boolean = false,
) {
    textSize = sizeSp
    setTextColor(color)
    if (medium) typeface = mediumTypeface
    letterSpacing = letterSpacingEm
    isAllCaps = allCaps
}

/** The circular icon control every surface uses: tinted vector on a
 *  circular ripple-able background, 48dp floor (a11y house rule). */
fun Context.iconButton(
    iconRes: Int,
    iconTint: Int,
    backgroundColor: Int,
    sizeDp: Int = 48,
    contentDescriptionText: CharSequence? = null,
): AppCompatImageButton = AppCompatImageButton(this).apply {
    setImageResource(iconRes)
    imageTintList = ColorStateList.valueOf(iconTint)
    background = circleBackground(backgroundColor).withRipple(this@iconButton)
    backgroundTintList = null
    val side = dp(sizeDp)
    minimumWidth = side
    minimumHeight = side
    layoutParams = android.widget.LinearLayout.LayoutParams(side, side)
    contentDescription = contentDescriptionText
    isClickable = true
    isFocusable = true
}

/** Load a vector drawable through AppCompat (tint-compatible). */
fun Context.vector(iconRes: Int): Drawable? = AppCompatResources.getDrawable(this, iconRes)

/** Respect the system's reduced-motion setting (animator scale 0). */
fun Context.animationsEnabled(): Boolean =
    android.provider.Settings.Global.getFloat(
        contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f

/** The one orchestrated moment: a slow breathing alpha for live
 *  things (the line dot, the answer pad). Returns the animator so the
 *  caller can cancel it; null when animations are disabled. */
fun pulse(view: View, context: Context): ValueAnimator? {
    if (!context.animationsEnabled()) return null
    return ValueAnimator.ofFloat(1f, 0.45f).apply {
        duration = 900
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animator -> view.alpha = animator.animatedValue as Float }
        start()
    }
}
