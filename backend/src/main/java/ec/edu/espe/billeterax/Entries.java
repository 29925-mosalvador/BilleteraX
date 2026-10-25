package ec.edu.espe.billeterax;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.List;
public interface Entries extends JpaRepository<LedgerEntry,String> {
    List<LedgerEntry> findByAccountIdOrderByOccurredAtDescIdDesc(String accountId);
    Page<LedgerEntry> findByAccountId(String accountId, Pageable pageable);
}
