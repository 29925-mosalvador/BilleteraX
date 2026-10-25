package ec.edu.espe.billeterax;


import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class ApiModels {
    public record MoneyRequest(
        @NotNull UUID operationId,
        @NotBlank @Pattern(regexp="CTA[0-9]{3,12}") String accountId,
        @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
        @Size(max=16) String destinationAccountId,
        @Size(max=20) String service,
        @Size(max=20) String contract) {}
    public record Balance(String accountId, String ownerName, BigDecimal balance, long version) {}
    public record Snapshot(Balance account, List<LedgerEntry> transactions, Instant syncedAt) {}
    public record Receipt(String operationId, LedgerEntry transaction, BigDecimal balance, boolean replayed) {}
    public record Event(String eventId, String type, String accountId, String transactionId,
                        BigDecimal amount, BigDecimal balance, Instant occurredAt) {}
    public record Committed(List<Event> events) {}
    public record ErrorBody(String code, String message) {}
}
