package com.masroufi.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BillNameTest {
    @Test fun aLeadingOrTrailingBillWordIsDropped() {
        assertEquals("الكهربا", billTitle("فاتورة الكهربا"))
        assertEquals("Electricity", billTitle("Electricity bill"))
        assertEquals("Water", billTitle("Bill Water"))
        assertEquals("Water", billTitle("  bill   Water bill "))
    }

    @Test fun otherNamesStayAsTheyAre() {
        assertEquals("النت", billTitle("النت"))
        assertEquals("فاتورتي", billTitle("فاتورتي"))
        assertEquals("Billboard rent", billTitle("Billboard rent"))
    }
}
