package ec.edu.espe.billeterax;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity
@Table(name="ledger_entries", indexes=@Index(name="idx_ledger_account_time",columnList="accountId,occurredAt"))
public class LedgerEntry {
    @Id public String id;
    @Column(nullable=false) public String operationId;
    @Column(nullable=false) public String accountId;
    @Column(nullable=false) public String type;
    @Column(nullable=false) public String category;
    @Column(nullable=false,precision=19,scale=2) public BigDecimal amount;
    @Column(nullable=false,precision=19,scale=2) public BigDecimal balanceAfter;
    @Column(nullable=false) public Instant occurredAt;
    public String counterparty;
    public String service;
    public String contract;
    @Column(nullable=false) public String status="CONFIRMED";
}
