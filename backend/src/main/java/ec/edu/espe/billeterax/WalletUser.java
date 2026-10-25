package ec.edu.espe.billeterax;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="wallet_users")
public class WalletUser {
    @Id public String id;
    @Column(nullable=false, unique=true, length=30) public String username;
    @Column(nullable=false, length=60) public String displayName;
    @Column(nullable=false, length=100) public String passwordHash;
    @Column(nullable=false) public Instant createdAt;
    @OneToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="account_id", nullable=false, unique=true) public Account account;
    protected WalletUser() {}
}
