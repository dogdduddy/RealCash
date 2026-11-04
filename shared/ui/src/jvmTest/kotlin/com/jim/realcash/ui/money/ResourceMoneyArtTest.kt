package com.jim.realcash.ui.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.ui.theme.RealCashTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResourceMoneyArtTest {
    @Test
    fun reviewedBillsAndCoinsLoadSynchronously() {
        // The distinct bill heights also catch swapped denominations / the unused draft.
        val expectedHeights = mapOf(
            Denomination.W50000 to 452f,
            Denomination.W10000 to 470f,
            Denomination.W5000 to 490f,
            Denomination.W1000 to 512f,
            Denomination.C500 to 512f,
            Denomination.C100 to 512f,
            Denomination.C50 to 512f,
            Denomination.C10 to 512f,
        )
        val loaded = mutableSetOf<Denomination>()
        val scene = ImageComposeScene(360, 600) {
            RealCashTheme {
                Column {
                    Denomination.entries.forEach { denomination ->
                        val painter = ResourceMoneyArt.painter(denomination)
                        assertNull(MoneyArt.None.painter(denomination))
                        assertNotNull(painter, "missing reviewed artwork for $denomination")
                        val isBill = denomination.kind == Denomination.Kind.BILL
                        assertEquals(if (isBill) 1024f else 512f, painter.intrinsicSize.width)
                        assertEquals(expectedHeights.getValue(denomination), painter.intrinsicSize.height)
                        if (isBill) {
                            BillStack(denomination, 3, Modifier.width(300.dp), painter)
                        } else {
                            CoinStack(denomination, 3, painter = painter)
                        }
                        loaded += denomination
                    }
                }
            }
        }
        try {
            scene.render()
            assertEquals(expectedHeights.keys, loaded)
        } finally {
            scene.close()
        }
    }
}
