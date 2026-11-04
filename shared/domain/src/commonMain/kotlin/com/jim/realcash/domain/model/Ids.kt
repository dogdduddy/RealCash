package com.jim.realcash.domain.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/** Identifier of a single [CashUnit] (one bill or one coin). */
@Serializable
@JvmInline
value class CashUnitId(val value: String)

/** Identifier of a [Hand] (cash taken out of the wallet in advance). */
@Serializable
@JvmInline
value class HandId(val value: String)

/** Identifier of a [DetectedPayment]. */
@Serializable
@JvmInline
value class PaymentId(val value: String)
