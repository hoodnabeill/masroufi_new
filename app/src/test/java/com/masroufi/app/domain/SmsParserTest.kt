package com.masroufi.app.domain

import com.masroufi.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {
    @Test fun vodafoneCashTransferWithBalance() {
        val p = SmsParser.parse("تم تحويل 150 جنيه إلى 01012345482. رصيدك الحالي 1,177 جنيه. فودافون كاش")!!
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("150", p.amount)
        assertEquals("1177", p.balanceAfter)
        assertEquals(SmsSource.VODAFONE_CASH, p.source)
        assertEquals("01012345482", p.counterparty)
    }

    @Test fun receivedMoneyIsIncome() {
        val p = SmsParser.parse("تم استلام مبلغ ٥٠٠ جنيه من أحمد. رصيدك ١٬٦٧٧ جنيه")!!
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals("500", p.amount)
        assertEquals("1677", p.balanceAfter)
        assertEquals("أحمد", p.counterparty)
    }

    @Test fun englishCardPurchase() {
        val p = SmsParser.parse("Your card ending 1234 was charged EGP 250.50 at CARREFOUR MAADI on 02/11. Available balance EGP 9,100.00")!!
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("250.5", p.amount)
        assertEquals("9100", p.balanceAfter)
        assertEquals(SmsSource.BANK, p.source)
        assertEquals("CARREFOUR MAADI", p.counterparty)
    }

    @Test fun atmWithdrawalIsATransferToCash() {
        assertEquals(TransactionType.TRANSFER, SmsParser.parse("ATM withdrawal of EGP 1,000.00 from account **55")!!.type)
    }

    @Test fun noAmountMeansNoParse() {
        assertNull(SmsParser.parse("كود التفعيل الخاص بك هو 4821"))
    }
}
