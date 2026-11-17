package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {
    private fun p(s: String) = parseSms(s)!!

    @Test fun mockDebit() {
        val r = p("تم خصم 40 جنيه من محفظتك. رصيدك الحالي 1,150 جنيه.")
        assertEquals(4000L, r.amount)
        assertEquals(SmsDirection.SPENT, r.direction)
        assertEquals(115000L, r.balance)
        assertEquals("محفظتك", r.party)
    }

    @Test fun arabicCredit() {
        val r = p("تم إضافة 1,500.00 ج.م إلى حسابك. الرصيد المتاح 3,400.50 ج.م")
        assertEquals(150000L, r.amount)
        assertEquals(SmsDirection.RECEIVED, r.direction)
        assertEquals(340050L, r.balance)
    }

    @Test fun transferToName() {
        val r = p("تم تحويل 500 جنيه الى احمد محمد. رصيدك 200 جنيه")
        assertEquals(50000L, r.amount)
        assertEquals(SmsDirection.MOVED_OUT, r.direction)
        assertEquals("احمد محمد", r.party)
        assertEquals(20000L, r.balance)
    }

    @Test fun transferToPhoneNotAmount() {
        val r = p("تم تحويل 200 جنيه إلى 01012345678")
        assertEquals(20000L, r.amount)
        assertEquals(SmsDirection.MOVED_OUT, r.direction)
        assertEquals("01012345678", r.party)
    }

    @Test fun transferToYouIsReceived() {
        val r = p("وصلك تحويل 300 جنيه إليك من سارة")
        assertEquals(30000L, r.amount)
        assertEquals(SmsDirection.RECEIVED, r.direction)
        assertEquals("سارة", r.party)
    }

    @Test fun atmWithdrawal() {
        val r = p("تم سحب 1,000 جنيه من ماكينة الصراف. رصيدك 4,000 جنيه")
        assertEquals(100000L, r.amount)
        assertEquals(SmsDirection.MOVED_OUT, r.direction)
        assertEquals(400000L, r.balance)
    }

    @Test fun englishDebit() {
        val r = p("Your account was debited EGP 250.75 at Fresh Mart. Balance: EGP 1,000.00")
        assertEquals(25075L, r.amount)
        assertEquals(SmsDirection.SPENT, r.direction)
        assertEquals("Fresh Mart", r.party)
        assertEquals(100000L, r.balance)
    }

    @Test fun englishCredit() {
        val r = p("You have received EGP 1,200.00 from Sam. Available balance EGP 2,000.00")
        assertEquals(120000L, r.amount)
        assertEquals(SmsDirection.RECEIVED, r.direction)
        assertEquals("Sam", r.party)
        assertEquals(200000L, r.balance)
    }

    @Test fun arabicIndicDigits() {
        val r = p("تم دفع ٣٥٠ جنيه")
        assertEquals(35000L, r.amount)
        assertEquals(SmsDirection.SPENT, r.direction)
        assertNull(r.balance)
    }

    @Test fun arabicIndicDecimalAndThousands() {
        val r = p("تم خصم ١٬٢٠٠٫٥٠ جنيه")
        assertEquals(120050L, r.amount)
    }

    @Test fun noBalanceNoParty() {
        val r = p("Purchase EGP 99 done")
        assertEquals(9900L, r.amount)
        assertNull(r.balance)
        assertNull(r.party)
    }

    @Test fun thousandsAndOneDecimal() {
        assertEquals(1234550L, p("Purchase EGP 12,345.5 at Store").amount)
    }

    @Test fun nearestKeywordWinsCredit() {
        assertEquals(SmsDirection.RECEIVED, p("تم إضافة 100 جنيه بعد خصم الرسوم").direction)
    }

    @Test fun nearestKeywordWinsDebit() {
        val r = p("تم خصم 75 جنيه وتم اضافة مكافأة")
        assertEquals(SmsDirection.SPENT, r.direction)
        assertEquals(7500L, r.amount)
    }

    @Test fun balanceOnlyIsNull() {
        assertNull(parseSms("رصيدك الحالي 1,150 جنيه"))
    }

    @Test fun nonsenseIsNull() {
        assertNull(parseSms("hello world"))
        assertNull(parseSms("كل سنة وانت طيب"))
        assertNull(parseSms("تم خصم الرسوم"))
        assertNotNull(parseSms("sent 5 EGP"))
    }
}
