package com.jim.realcash.ui.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.ui.resources.Res
import com.jim.realcash.ui.resources.bill_w50000
import com.jim.realcash.ui.resources.bill_w10000
import com.jim.realcash.ui.resources.bill_w5000
import com.jim.realcash.ui.resources.bill_w1000
import com.jim.realcash.ui.resources.coin_c500
import com.jim.realcash.ui.resources.coin_c100
import com.jim.realcash.ui.resources.coin_c50
import com.jim.realcash.ui.resources.coin_c10
import org.jetbrains.compose.resources.painterResource

/**
 * Source of denomination artwork. [MoneyArt.None] keeps previews without artwork on Canvas placeholders.
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

/** Reviewed bill and coin artwork bundled by Compose resources on Android, iOS and desktop. */
object ResourceMoneyArt : MoneyArt {
    @Composable
    override fun painter(denomination: Denomination): Painter? = when (denomination) {
        Denomination.W50000 -> painterResource(Res.drawable.bill_w50000)
        Denomination.W10000 -> painterResource(Res.drawable.bill_w10000)
        Denomination.W5000 -> painterResource(Res.drawable.bill_w5000)
        Denomination.W1000 -> painterResource(Res.drawable.bill_w1000)
        Denomination.C500 -> painterResource(Res.drawable.coin_c500)
        Denomination.C100 -> painterResource(Res.drawable.coin_c100)
        Denomination.C50 -> painterResource(Res.drawable.coin_c50)
        Denomination.C10 -> painterResource(Res.drawable.coin_c10)
    }
}

/** The [MoneyArt] used by the screens; standalone previews retain the Canvas fallback. */
val LocalMoneyArt = staticCompositionLocalOf<MoneyArt> { MoneyArt.None }
