package ec.edu.espe.billeterax.data


import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions", primaryKeys = ["scope", "id"], indices = [Index("scope")])
data class LocalTransaction(
    val scope: String, val id: String, val operationId: String, val accountId: String,
    val type: String, val category: String, val amountCents: Long, val balanceAfterCents: Long,
    val occurredAt: String, val counterparty: String?, val service: String?, val contract: String?, val status: String
)
@Entity(tableName = "balances")
data class LocalBalance(@PrimaryKey val scope: String, val ownerName: String, val cents: Long, val syncedAt: String)
@Entity(tableName = "pending_operations")
data class PendingOperation(@PrimaryKey val scope: String, val operationId: String, val route: String, val body: String)

@Dao
interface WalletDao {
    @Query("SELECT * FROM transactions WHERE scope = :scope ORDER BY occurredAt DESC, id DESC")
    fun observeTransactions(scope: String): Flow<List<LocalTransaction>>
    @Query("SELECT * FROM balances WHERE scope = :scope")
    fun observeBalance(scope: String): Flow<LocalBalance?>
    @Query("SELECT * FROM pending_operations WHERE scope = :scope")
    fun observePending(scope: String): Flow<PendingOperation?>
    @Query("SELECT * FROM pending_operations WHERE scope = :scope")
    suspend fun pending(scope: String): PendingOperation?
    @Upsert suspend fun upsertTransactions(items: List<LocalTransaction>)
    @Upsert suspend fun upsertBalance(item: LocalBalance)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPending(item: PendingOperation)
    @Query("DELETE FROM pending_operations WHERE scope = :scope") suspend fun clearPending(scope: String)
}
@Database(entities = [LocalTransaction::class, LocalBalance::class, PendingOperation::class], version = 1, exportSchema = true)
abstract class WalletDatabase : RoomDatabase() { abstract fun dao(): WalletDao }
