package com.jim.realcash.ui.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import com.jim.realcash.domain.model.Denomination

/**
 * Source of denomination artwork. The image pipeline (design doc §5.5) will provide an implementation backed by
 * bundled resources; until then [None] makes every face fall back to its Canvas placeholder.
 */
interface MoneyArt {
    /** Artwork for [denomination], or `null` for the placeholder. */
    @Composable
    fun painter(denomination: Denomination): Painter?

    /** No artwork: Canvas placeholders everywhere. */
    object None : MoneyArt {
        @Composable
        override fun painter(denomination: Denomination): Painter? = null
    }
}

/** The [MoneyArt] used by the screens. Provide a real one at the root once pipeline images exist. */
val LocalMoneyArt = staticCompositionLocalOf<MoneyArt> { MoneyArt.None }
