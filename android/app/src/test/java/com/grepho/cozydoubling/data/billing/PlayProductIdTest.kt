package com.grepho.cozydoubling.data.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayProductIdTest {
    @Test
    fun subscriptionIdDropsBasePlan() {
        assertEquals("cozy_supporter_monthly", playProductId("cozy_supporter_monthly:monthly-base"))
    }

    @Test
    fun plainProductIdIsUnchanged() {
        assertEquals("cozy_supporter_lifetime", playProductId("cozy_supporter_lifetime"))
    }
}
