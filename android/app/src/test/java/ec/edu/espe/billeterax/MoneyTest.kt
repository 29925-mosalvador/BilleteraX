package ec.edu.espe.billeterax
import ec.edu.espe.billeterax.model.Money
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
class MoneyTest {
    @Test fun exactDecimalConversion() {
        assertEquals(10L, Money.cents("0.10")); assertEquals(1234L, Money.cents("12,34"))
        assertEquals(BigDecimal("0.30"), Money.decimal(Money.cents("0.10") + Money.cents("0.20")))
        assertEquals(-1234L, Money.fromDecimal(BigDecimal("-12.34")))
    }
    @Test fun rejectsInvalidAndFractionalCents() {
        for(value in listOf("0", "-1", "1.001", "NaN", "1e3", "1000000000000", "1,2.3"))
            assertTrue("Must reject $value", runCatching { Money.cents(value) }.isFailure)
    }
}
