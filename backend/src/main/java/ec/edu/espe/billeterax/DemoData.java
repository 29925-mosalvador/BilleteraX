package ec.edu.espe.billeterax;


import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class DemoData implements CommandLineRunner {
    private final Accounts accounts;
    DemoData(Accounts accounts) { this.accounts=accounts; }
    @Override @Transactional public void run(String... args) {
        if(!accounts.existsById("CTA001")) accounts.save(new Account("CTA001","Usuario A","100.00"));
        if(!accounts.existsById("CTA002")) accounts.save(new Account("CTA002","Usuario B","50.00"));
    }
}
