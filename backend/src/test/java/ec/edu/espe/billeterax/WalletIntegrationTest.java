package ec.edu.espe.billeterax;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static ec.edu.espe.billeterax.ApiModels.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:wallet-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000", "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("dev")
class WalletIntegrationTest {
    @Autowired WalletService wallet;
    @Autowired Accounts accounts;
    @Autowired Entries entries;
    @Autowired Operations operations;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;
    final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @BeforeEach void reset() {
        operations.deleteAll(); entries.deleteAll(); accounts.deleteAll();
        accounts.save(new Account("CTA001","Usuario A","100.00"));
        accounts.save(new Account("CTA002","Usuario B","50.00"));
    }
    MoneyRequest req(String account,String amount,String dest) {
        return new MoneyRequest(UUID.randomUUID(),account,new BigDecimal(amount),dest,null,null);
    }
    void balance(String id,String expected) { assertEquals(0,new BigDecimal(expected).compareTo(wallet.balance(id,id).balance())); }
    HttpResponse<String> get(String path,String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String path,MoneyRequest r,String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(r))).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void depositWithdrawAndSnapshot() throws Exception {
        assertEquals(200,get("/api/accounts/CTA001/balance","demo-cta001-local-only").statusCode());
        assertEquals(200,post("/api/transactions/deposit",req("CTA001","10.25",null),"demo-cta001-local-only").statusCode());
        assertEquals(200,post("/api/transactions/withdraw",req("CTA001","0.25",null),"demo-cta001-local-only").statusCode());
        balance("CTA001","110.00");
        assertEquals(2,wallet.snapshot("CTA001","CTA001").transactions().size());
    }
    @Test void insufficientFundsRollsBack() throws Exception {
        var response=post("/api/transactions/withdraw",req("CTA001","101.00",null),"demo-cta001-local-only");
        assertEquals(422,response.statusCode()); assertTrue(response.body().contains("INSUFFICIENT_FUNDS"));
        balance("CTA001","100.00"); assertEquals(0,entries.count()); assertEquals(0,operations.count());
    }
    @Test void atomicTransferAndHistory() {
        wallet.execute("CTA002","TRANSFER",req("CTA002","20.00","CTA001"));
        balance("CTA001","120.00"); balance("CTA002","30.00"); assertEquals(2,entries.count());
        assertEquals("TRANSFER_IN",wallet.history("CTA001","CTA001",0,50).getContent().getFirst().type);
    }
    @Test void nonexistentDestinationRollsBack() {
        assertThrows(ApiException.class,()->wallet.execute("CTA001","TRANSFER",req("CTA001","20.00","CTA999")));
        balance("CTA001","100.00"); assertEquals(0,entries.count());
    }
    @Test void servicePaymentAndContract() throws Exception {
        var valid=new MoneyRequest(UUID.randomUUID(),"CTA001",new BigDecimal("12.34"),null,"WATER","AGUA-12345");
        assertEquals(200,post("/api/payments/services",valid,"demo-cta001-local-only").statusCode());
        balance("CTA001","87.66"); assertEquals("Servicio Básico",entries.findAll().getFirst().category);
        var bad=new MoneyRequest(UUID.randomUUID(),"CTA001",BigDecimal.ONE,null,"WATER","x");
        assertEquals(400,post("/api/payments/services",bad,"demo-cta001-local-only").statusCode());
    }
    @Test void validatesAmountsAndSelfTransfer() throws Exception {
        for(String amount:List.of("0","-1","1.001","1000000000000"))
            assertEquals(400,post("/api/transactions/deposit",req("CTA001",amount,null),"demo-cta001-local-only").statusCode());
        assertEquals(400,post("/api/transactions/transfer",req("CTA001","1.00","CTA001"),"demo-cta001-local-only").statusCode());
    }
    @Test void authorizationPreventsReadingOrSpendingOtherAccount() throws Exception {
        assertEquals(401,get("/api/accounts/CTA001/balance","bad").statusCode());
        assertEquals(403,get("/api/accounts/CTA002/snapshot","demo-cta001-local-only").statusCode());
        assertEquals(403,post("/api/transactions/withdraw",req("CTA002","1.00",null),"demo-cta001-local-only").statusCode());
    }
    @Test void idempotencyReplaysReceiptAndRejectsDifferentPayload() {
        var r=req("CTA001","10.00",null);
        Receipt first=wallet.execute("CTA001","DEPOSIT",r);
        Receipt replay=wallet.execute("CTA001","DEPOSIT",r);
        assertTrue(replay.replayed()); assertEquals(first.transaction().id,replay.transaction().id);
        assertThrows(ApiException.class,()->wallet.execute("CTA001","DEPOSIT",new MoneyRequest(r.operationId(),"CTA001",new BigDecimal("11.00"),null,null,null)));
        balance("CTA001","110.00"); assertEquals(1,entries.count());
    }
    void parallel(List<Callable<Void>> tasks) throws Exception {
        try(var executor=Executors.newFixedThreadPool(12)) {
            for(Future<Void> f:executor.invokeAll(tasks)) f.get(30,TimeUnit.SECONDS);
        }
    }
    @Test void concurrentDuplicateRequestsCreditExactlyOnce() throws Exception {
        var r=req("CTA001","5.00",null); List<Callable<Void>> tasks=new ArrayList<>();
        for(int i=0;i<30;i++) tasks.add(()->{wallet.execute("CTA001","DEPOSIT",r);return null;});
        parallel(tasks); balance("CTA001","105.00"); assertEquals(1,entries.count());
    }
    @Test void concurrentWithdrawalsNeverOverdraw() throws Exception {
        List<Callable<Void>> tasks=new ArrayList<>();
        for(int i=0;i<30;i++) tasks.add(()->{
            try { wallet.execute("CTA001","WITHDRAW",req("CTA001","10.00",null)); }
            catch(ApiException e) {assertEquals("INSUFFICIENT_FUNDS",e.code);} return null;
        });
        parallel(tasks); balance("CTA001","0.00"); assertEquals(10,entries.count());
    }
    @Test void concurrentOppositeTransfersConserveTotalAndDoNotDeadlock() throws Exception {
        List<Callable<Void>> tasks=new ArrayList<>();
        for(int i=0;i<40;i++) {
            final boolean forward=i%2==0;
            tasks.add(()->{String from=forward?"CTA001":"CTA002",to=forward?"CTA002":"CTA001";
                wallet.execute(from,"TRANSFER",req(from,"1.00",to));return null;});
        }
        parallel(tasks); balance("CTA001","100.00"); balance("CTA002","50.00"); assertEquals(80,entries.count());
    }
    static class SocketListener implements WebSocket.Listener {
        final BlockingQueue<String> messages=new LinkedBlockingQueue<>(); final StringBuilder parts=new StringBuilder();
        public void onOpen(WebSocket ws){ws.request(1);}
        public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){
            parts.append(data); if(last){messages.add(parts.toString());parts.setLength(0);}ws.request(1);return null;
        }
    }
    WebSocket socket(String token,SocketListener l) {
        return http.newWebSocketBuilder().header("Authorization","Bearer "+token)
            .buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws"),l).join();
    }
    @Test void realWebSocketNotifiesOnlyAuthorizedAccountAfterCommit() throws Exception {
        var a=new SocketListener();var b=new SocketListener();
        var sa=socket("demo-cta001-local-only",a);var sb=socket("demo-cta002-local-only",b);
        try {
            assertTrue(a.messages.poll(5,TimeUnit.SECONDS).contains("CONNECTED"));
            assertTrue(b.messages.poll(5,TimeUnit.SECONDS).contains("CONNECTED"));
            wallet.execute("CTA001","DEPOSIT",req("CTA001","1.00",null));
            JsonNode event=mapper.readTree(a.messages.poll(5,TimeUnit.SECONDS));
            assertEquals("CTA001",event.get("accountId").asText());
            assertTrue(entries.existsById(event.get("transactionId").asText()));
            assertNull(b.messages.poll(250,TimeUnit.MILLISECONDS));
            wallet.execute("CTA002","TRANSFER",req("CTA002","20.00","CTA001"));
            JsonNode incoming=mapper.readTree(a.messages.poll(5,TimeUnit.SECONDS));
            assertEquals("TRANSFER_RECEIVED",incoming.get("type").asText());
            assertEquals(0,new BigDecimal("121.00").compareTo(incoming.get("balance").decimalValue()));
            assertThrows(ApiException.class,()->wallet.execute("CTA001","WITHDRAW",req("CTA001","999.00",null)));
            assertNull(a.messages.poll(250,TimeUnit.MILLISECONDS));
        } finally {sa.abort();sb.abort();}
    }
    @Test void websocketRejectsInvalidCredential() {
        assertThrows(CompletionException.class,()->socket("bad",new SocketListener()));
    }
    @Test void healthIsAvailableAndOtherManagementEndpointsHidden() throws Exception {
        assertEquals(200,get("/actuator/health","").statusCode());
        assertEquals(404,get("/actuator/env","").statusCode());
    }
}
