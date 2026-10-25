package ec.edu.espe.billeterax;


import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface Accounts extends JpaRepository<Account,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> locked(String id);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> snapshotLock(String id);
}
