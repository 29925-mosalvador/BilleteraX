package ec.edu.espe.billeterax.model


import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

object Money {
    fun cents(raw: String): Long {
        require(raw.matches(Regex("[0-9]{1,12}([.,][0-9]{1,2})?"))) { "Ingresa un monto con máximo dos decimales" }
        return BigDecimal(raw.replace(',', '.')).movePointRight(2).longValueExact().also {
            require(it > 0) { "El monto debe ser mayor que cero" }
        }
    }
    fun fromDecimal(value: BigDecimal): Long = value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    fun decimal(cents: Long): BigDecimal = BigDecimal.valueOf(cents, 2)
    fun display(cents: Long): String = java.text.NumberFormat.getCurrencyInstance(Locale.US).format(decimal(cents))
}
data class UserProfile(val id: String, val username: String, val displayName: String, val accountId: String)
enum class Appearance(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Oscuro") }
data class Config(val url: String = "http://192.168.18.76:8080/", val account: String = "", val token: String = "") {
    val scope: String get() = "$url|$account"
}
enum class Operation(val title: String, val route: String) {
    DEPOSIT("Depositar", "transactions/deposit"), WITHDRAW("Retirar", "transactions/withdraw"),
    TRANSFER("Transferir", "transactions/transfer"), SERVICE("Pagar servicios", "payments/services")
}
enum class Connection(val label: String) {
    OFFLINE("Desconectado"), CONNECTING("Conectando"), CONNECTED("En línea"), RECONNECTING("Reconectando")
}
