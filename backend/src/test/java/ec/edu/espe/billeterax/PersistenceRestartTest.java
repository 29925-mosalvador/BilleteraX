package ec.edu.espe.billeterax;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import static ec.edu.espe.billeterax.ApiModels.*;
import static org.junit.jupiter.api.Assertions.*;
class PersistenceRestartTest {
    @TempDir Path directory;
    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(BilleteraApplication.class).profiles("dev").run(
            "--server.port=0", "--spring.datasource.url=jdbc:h2:file:"+directory.resolve("wallet").toString().replace('\\','/')+";DB_CLOSE_ON_EXIT=FALSE",
            "--spring.jpa.hibernate.ddl-auto=update");
    }
    @Test void fileDatabaseAndIdempotencySurviveServerRestart() throws Exception {
        var request=new MoneyRequest(UUID.randomUUID(),"CTA001",new BigDecimal("7.25"),null,null,null);
        String receiptId;
        AuthModels.Authentication registered;
        try(var first=start()) {
            receiptId=first.getBean(WalletService.class).execute("CTA001","DEPOSIT",request).transaction().id;
            registered=first.getBean(UserAuthService.class).register(new AuthModels.Registration("Persistencia","persistencia","Clave-prueba-24"));
        }
        // Some restricted Windows tokens report NIO isWritable=false despite allowing creation.
        // H2 treats an existing file as read-only in that environment. Run again outside it.
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isWritable(directory.resolve("wallet.mv.db")),
            "NIO reports the test file read-only; repeat this persistence test in an unrestricted shell or Debian");
        try(var second=start()) {
            var wallet=second.getBean(WalletService.class);
            var auth=second.getBean(UserAuthService.class);
            assertEquals(registered.user().accountId(),auth.authenticate(registered.token()).accountId());
            assertEquals(registered.user().accountId(),auth.login(new AuthModels.Login("persistencia","Clave-prueba-24")).user().accountId());
            assertEquals(0,new BigDecimal("107.25").compareTo(wallet.balance("CTA001","CTA001").balance()));
            assertEquals(1,wallet.snapshot("CTA001","CTA001").transactions().size());
            var replay=wallet.execute("CTA001","DEPOSIT",request);
            assertTrue(replay.replayed()); assertEquals(receiptId,replay.transaction().id);
        }
    }
}
