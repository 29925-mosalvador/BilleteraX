package ec.edu.espe.billeterax;


import jakarta.persistence.*;
@Entity
@Table(name="wallet_operations")
public class WalletOperation {
    @Id public String id;
    @Column(nullable=false,length=600) public String fingerprint;
    @Column(nullable=false) public String receiptId;
    protected WalletOperation() {}
    WalletOperation(String id, String fingerprint, String receiptId) {
        this.id=id; this.fingerprint=fingerprint; this.receiptId=receiptId;
    }
}
