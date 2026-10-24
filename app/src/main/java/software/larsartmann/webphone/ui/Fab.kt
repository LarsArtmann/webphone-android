package software.larsartmann.webphone.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import androidx.core.content.ContextCompat
import software.larsartmann.webphone.R

/**
 * The one floating-action look the shell's native chrome uses: a
 * rounded teal pill with a white label — the launcher's teal
 * (colors.xml accent), so the one native control reads as THE way to
 * call from anywhere in the app.
 */
fun Button.styleAsFab(cornerRadiusDp: Float = 28f) {
    val density = resources.displayMetrics.density
    background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerRadiusDp * density
        setColor(ContextCompat.getColor(context, R.color.accent))
    }
    setTextColor(Color.WHITE)
    // The a11y floor: 48dp square touch targets.
    minimumHeight = (48 * density).toInt()
    minWidth = (48 * density).toInt()
}
