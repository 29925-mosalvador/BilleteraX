package ec.edu.espe.billeterax.network


import com.google.gson.Gson
import ec.edu.espe.billeterax.data.LocalTransaction
import ec.edu.espe.billeterax.model.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

data class MoneyRequest(val operationId: String, val accountId: String, val amount: BigDecimal,
                        val destinationAccountId: String? = null, val service: String? = null, val contract: String? = null)
data class BalanceDto(val accountId: String, val ownerName: String, val balance: BigDecimal, val version: Long)
data class TransactionDto(val id: String, val operationId: String, val accountId: String, val type: String,
    val category: String, val amount: BigDecimal, val balanceAfter: BigDecimal, val occurredAt: String,
    val counterparty: String?, val service: String?, val contract: String?, val status: String) {
    fun local(scope: String) = LocalTransaction(scope, id, operationId, accountId, type, category,
        Money.fromDecimal(amount), Money.fromDecimal(balanceAfter), occurredAt, counterparty, service, contract, status)
}
data class SnapshotDto(val account: BalanceDto, val transactions: List<TransactionDto>, val syncedAt: String)
data class ReceiptDto(val operationId: String, val transaction: TransactionDto, val balance: BigDecimal, val replayed: Boolean)
data class EventDto(val eventId: String, val type: String, val accountId: String, val transactionId: String)
data class ErrorDto(val code: String?, val message: String?)
data class RegistrationRequest(val displayName: String, val username: String, val password: String)
data class LoginRequest(val username: String, val password: String)
data class AuthenticationDto(val token: String, val expiresAt: String, val user: UserProfile)
interface WalletApi {
    @POST("api/auth/register") suspend fun register(@Body request: RegistrationRequest): AuthenticationDto
    @POST("api/auth/login") suspend fun login(@Body request: LoginRequest): AuthenticationDto
    @GET("api/auth/me") suspend fun me(): UserProfile
    @POST("api/auth/logout") suspend fun logout(): retrofit2.Response<Unit>
    @GET("api/accounts/{id}/snapshot") suspend fun snapshot(@Path("id") id: String): SnapshotDto
    @POST("api/{route}") suspend fun execute(@Path(value = "route", encoded = true) route: String, @Body request: MoneyRequest): ReceiptDto
}
class WalletNetwork(val config: Config) {
    val gson = Gson()
    val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).pingInterval(15, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${config.token}").build()) }.build()
    val api: WalletApi = Retrofit.Builder().baseUrl(config.url).client(client)
        .addConverterFactory(GsonConverterFactory.create(gson)).build().create(WalletApi::class.java)
    fun close() { client.dispatcher.cancelAll(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
}
