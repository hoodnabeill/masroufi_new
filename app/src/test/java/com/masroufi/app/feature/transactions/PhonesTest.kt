package com.masroufi.app.feature.transactions

import com.masroufi.app.ui.components.Phones
import org.junit.Assert.assertEquals
import org.junit.Test

class PhonesTest {
    @Test fun egyptianNumbersBecomeInternational() {
        assertEquals("201012345678", Phones.international("010 1234 5678"))
        assertEquals("201012345678", Phones.international("+20 101 234 5678"))
        assertEquals("201012345678", Phones.international("00201012345678"))
        assertEquals("201012345678", Phones.international("٠١٠١٢٣٤٥٦٧٨"))
    }
}
