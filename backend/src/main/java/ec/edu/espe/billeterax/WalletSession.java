package ec.edu.espe.billeterax;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="wallet_sessions", indexes=@Index(name="idx_session_expiry", columnList="expiresAt"))
public class WalletSession {
    @Id @Column(length=64) public String tokenHash;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="user_id", nullable=false) public WalletUser user;
    @Column(nullable=false) public Instant createdAt;
    @Column(nullable=false) public Instant expiresAt;
    protected WalletSession() {}
}
