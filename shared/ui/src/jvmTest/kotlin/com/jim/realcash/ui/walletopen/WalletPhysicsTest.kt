package com.jim.realcash.ui.walletopen

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Offset
import com.jim.realcash.domain.model.*
import kotlin.test.*
import kotlin.time.Instant

class WalletPhysicsTest {
    private val bill = CashUnit(CashUnitId("front"), Denomination.W10000, CashUnitState.IN_WALLET,
        Instant.parse("2026-11-04T09:00:00Z"), CashUnitSource.ALLOWANCE)
    private val tray = WalletRect(10f, 10f, 200f, 100f)
    private val wallet = WalletRect(20f, 300f, 220f, 400f)
    private val source = WalletRect(40f, 340f, 60f, 140f)

    @Test fun fingerFollowsTheLeadingEdgeAndReleasesOnlyBeyondClearance() {
        val strip = WalletRect(40f, 100f, 20f, 30f)
        assertEquals(SwayContact(SwayState.Rest, false), SwayState.contact(50f, 1, strip, 8f))
        assertEquals(SwayContact(SwayState(4f), false), SwayState.contact(64f, 1, strip, 8f))
        assertEquals(SwayContact(SwayState(7f), false), SwayState.contact(67f, 1, strip, 8f))
        assertEquals(SwayContact(SwayState(7f), true), SwayState.contact(68f, 1, strip, 8f))
        assertEquals(SwayContact(SwayState(-4f), false), SwayState.contact(36f, -1, strip, 8f))
        assertEquals(SwayContact(SwayState(-7f), true), SwayState.contact(32f, -1, strip, 8f))
        assertEquals(SwayContact(SwayState(10f), true), SwayState.contact(100f, 1, strip, 40f))
        assertEquals(SwayContact(SwayState.Rest, true), SwayState.contact(61f, 1, strip, 0.5f))
        assertEquals(0f, SwayState.limit(1f))
        assertEquals(10f, SwayState.limit(100f))
        assertEquals(0.35f, SwayState.DAMPING)
        assertEquals(420f, SwayState.STIFFNESS)
    }

    @Test fun displacementTiltsAroundXWithTwelveDegreeLimit() {
        assertEquals(60f, SwayState.rotationX(0f))
        assertEquals(54f, SwayState.rotationX(5f))
        assertEquals(66f, SwayState.rotationX(-5f))
        assertEquals(48f, SwayState.rotationX(10f))
        assertEquals(72f, SwayState.rotationX(-10f))
        assertEquals(48f, SwayState.rotationX(100f))
        assertEquals(72f, SwayState.rotationX(-100f))
    }

    @Test fun onlyTwoNeighborsReceiveHalfAndQuarterWithStagger() {
        assertEquals(listOf(SwayPulse(3, SwayState(4f), 20), SwayPulse(4, SwayState(2f), 40)),
            SwayState.neighbors(2, 1, 6, 8f))
        assertEquals(listOf(SwayPulse(1, SwayState(-4f), 20), SwayPulse(0, SwayState(-2f), 40)),
            SwayState.neighbors(2, -1, 6, -8f))
        assertEquals(listOf(SwayPulse(5, SwayState(4f), 20)), SwayState.neighbors(4, 1, 6, 8f))
        assertTrue(SwayState.neighbors(5, 1, 6, 8f).isEmpty())
        assertTrue(SwayState.neighbors(0, -1, 6, -8f).isEmpty())
    }

    @Test fun fastSwipeCrossesNarrowStripsButNotLeather() {
        val strip = WalletRect(40f, 100f, 3f, 30f)
        assertTrue(SwayState.crosses(Offset(0f, 110f), Offset(100f, 110f), strip))
        assertTrue(SwayState.crosses(Offset(100f, 110f), Offset(0f, 110f), strip))
        assertFalse(SwayState.crosses(Offset(0f, 150f), Offset(100f, 150f), strip))
        assertFalse(SwayState.crosses(Offset(0f, 110f), Offset(0f, 120f), strip))
    }

    @Test fun dropUsesTrayFloorAndOnlyTrayUnitsMayReturnToWallet() {
        val pick = PickState(bill, false, source, source.center)
        assertEquals(DropDestination.TRAY, pick.destination(tray.center, tray, wallet))
        assertEquals(DropDestination.SOURCE, pick.destination(wallet.center, tray, wallet))
        assertEquals(DropDestination.SOURCE, pick.destination(Offset(9f, 50f), tray, wallet))
        assertEquals(DropDestination.SOURCE, pick.destination(tray.center, tray, wallet, cancelled = true))
        assertEquals(DropDestination.WALLET, pick.copy(fromTray = true).destination(wallet.center, tray, wallet))
        assertEquals(90f, pick.rotation)
        assertEquals(DropFlight(tray, 0f, false), pick.flight(DropDestination.TRAY, tray))
        assertEquals(DropFlight(source, 90f, true), pick.flight(DropDestination.SOURCE, source))
        assertEquals(DropFlight(source, 90f, true), pick.copy(fromTray = true).flight(DropDestination.WALLET, source))
        assertEquals(source.height, pick.faceWidth)
        assertEquals(Offset(80f, 200f), pick.centerAt(Offset(80f, 200f)))
        assertEquals(400L, PickState.LONG_PRESS_MILLIS)
        assertEquals(300L, PickState.RETURN_MILLIS)
        assertEquals(1.08f, PickState.LIFT_SCALE)
    }

    @Test fun coinsKeepExactDropCenterAndCrowdedBillsStayInsideTray() {
        val coin = bill.copy(id = CashUnitId("coin"), denomination = Denomination.C500)
        val point = Offset(130f, 70f)
        assertEquals(point, traySlots(listOf(coin), tray, mapOf(coin.id to point)).getValue(coin.id).center)
        val many = List(30) { bill.copy(id = CashUnitId("bill-$it")) }
        val bounds = traySlots(many, tray, emptyMap()).values
        assertTrue(bounds.all { it.left >= tray.left && it.right <= tray.right + 0.001f })
    }

    @Test fun pointerMovesInvalidateOnlyLayerUntilTrayEdgeChanges() {
        val physics = WalletOpenPhysics().apply { this.tray = this@WalletPhysicsTest.tray }
        physics.start(PickState(bill, false, source, source.center))
        Snapshot.sendApplyNotifications()
        var invalidations = 0
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            observer.observeReads("composition", { _: String -> invalidations++ }) {
                physics.picked?.unit?.id
                physics.overTray
            }
            repeat(30) { physics.move(source.center + Offset(it.toFloat(), 0f)); Snapshot.sendApplyNotifications() }
            assertEquals(0, invalidations, "in-pocket movement must not recompose any bill")
            physics.move(tray.center)
            Snapshot.sendApplyNotifications()
            assertEquals(1, invalidations, "only crossing the tray boundary invalidates composition")
        } finally { observer.stop(); observer.clear() }
    }
}
