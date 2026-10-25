package ec.edu.espe.billeterax;


import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import static ec.edu.espe.billeterax.ApiModels.*;
@RestController
@RequestMapping("/api")
public class WalletController {
    private final WalletService wallet;
    WalletController(WalletService wallet) { this.wallet=wallet; }
    @GetMapping("/accounts/{id}/balance")
    Balance balance(@RequestAttribute("accountId") String actor,@PathVariable String id) { return wallet.balance(actor,id); }
    @GetMapping("/accounts/{id}/snapshot")
    Snapshot snapshot(@RequestAttribute("accountId") String actor,@PathVariable String id) { return wallet.snapshot(actor,id); }
    @GetMapping("/accounts/{id}/transactions")
    Page<LedgerEntry> history(@RequestAttribute("accountId") String actor,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return wallet.history(actor,id,page,size); }
    @PostMapping("/transactions/deposit")
    Receipt deposit(@RequestAttribute("accountId") String actor,@Valid @RequestBody MoneyRequest r) { return wallet.execute(actor,"DEPOSIT",r); }
    @PostMapping("/transactions/withdraw")
    Receipt withdraw(@RequestAttribute("accountId") String actor,@Valid @RequestBody MoneyRequest r) { return wallet.execute(actor,"WITHDRAW",r); }
    @PostMapping("/transactions/transfer")
    Receipt transfer(@RequestAttribute("accountId") String actor,@Valid @RequestBody MoneyRequest r) { return wallet.execute(actor,"TRANSFER",r); }
    @PostMapping("/payments/services")
    Receipt service(@RequestAttribute("accountId") String actor,@Valid @RequestBody MoneyRequest r) { return wallet.execute(actor,"SERVICE",r); }
}
