package ec.edu.espe.billeterax;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface UserSessions extends JpaRepository<WalletSession,String> {
    boolean existsByTokenHashAndExpiresAtAfter(String tokenHash, java.time.Instant expiresAt);
    @Query("select s from WalletSession s join fetch s.user u join fetch u.account where s.tokenHash = :hash")
    Optional<WalletSession> authenticated(String hash);
}
