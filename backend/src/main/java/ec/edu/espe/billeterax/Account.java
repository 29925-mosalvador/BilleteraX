package ec.edu.espe.billeterax;


import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity
@Table(name="accounts")
public class Account {
    @Id public String id;
    @Column(nullable=false) public String ownerName;
    @Column(nullable=false, precision=19, scale=2) public BigDecimal balance;
    @Version public long version;
    protected Account() {}
    Account(String id, String ownerName, String balance) {
        this.id=id; this.ownerName=ownerName; this.balance=new BigDecimal(balance);
    }
}
