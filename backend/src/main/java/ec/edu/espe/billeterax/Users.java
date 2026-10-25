package ec.edu.espe.billeterax;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
public interface Users extends JpaRepository<WalletUser,String> {
    @Query("select u from WalletUser u join fetch u.account where u.username = :username")
    Optional<WalletUser> findByUsername(String username);
    @Query("select u from WalletUser u join fetch u.account where u.account.id = :accountId")
    Optional<WalletUser> findByAccountId(String accountId);
    boolean existsByUsername(String username);
}
