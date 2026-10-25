package ec.edu.espe.billeterax;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:auth-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
    "spring.jpa.hibernate.ddl-auto=create-drop"})
class UserAuthenticationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired Users users;
    @Autowired UserSessions sessions;
    @Autowired Accounts accounts;
    @Autowired Entries entries;
    @Autowired Operations operations;
    final HttpClient http=HttpClient.newHttpClient();
    @BeforeEach void reset() {
        sessions.deleteAll(); users.deleteAll(); operations.deleteAll(); entries.deleteAll(); accounts.deleteAll();
        accounts.save(new Account("CTA001","Usuario A","100.00"));
        accounts.save(new Account("CTA002","Usuario B","50.00"));
    }
    HttpResponse<String> call(String method,String path,Object body,String token) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
        if(token!=null) b.header("Authorization","Bearer "+token);
        if(body!=null) b.header("Content-Type","application/json");
        return http.send(b.method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    Map<String,String> registration(String username) { return Map.of("displayName","Ana Pérez","username",username,"password","Prueba-segura-24"); }
    JsonNode register(String username) throws Exception {
        var response=call("POST","/api/auth/register",registration(username),null);
        assertEquals(201,response.statusCode(),response.body()); return mapper.readTree(response.body());
    }
    @Test void registrationCreatesUserAccountAndHashedSessionWithoutEmail() throws Exception {
        var auth=register("ana"); String token=auth.path("token").asText();
        assertEquals("Ana Pérez",auth.path("user").path("displayName").asText());
        assertEquals(64,token.length()); assertFalse(auth.toString().contains("passwordHash"));
        var stored=users.findByUsername("ana").orElseThrow();
        assertNotEquals("Prueba-segura-24",stored.passwordHash);
        assertTrue(new BCryptPasswordEncoder().matches("Prueba-segura-24",stored.passwordHash));
        assertFalse(sessions.findAll().getFirst().tokenHash.equals(token));
        String id=auth.path("user").path("accountId").asText();
        assertTrue(id.matches("CTA[0-9]{10}"));
        var snapshot=call("GET","/api/accounts/"+id+"/snapshot",null,token);
        assertEquals(200,snapshot.statusCode()); assertEquals(0,mapper.readTree(snapshot.body()).path("account").path("balance").decimalValue().signum());
        assertEquals("ana",mapper.readTree(call("GET","/api/auth/me",null,token).body()).path("username").asText());
        assertTrue(accounts.existsById("CTA001")); assertTrue(accounts.existsById("CTA002"));
    }
    @Test void loginNormalizesUsernameAndRejectsIncorrectCredentials() throws Exception {
        var registered=register("ana");
        var login=call("POST","/api/auth/login",Map.of("username"," ANA ","password","Prueba-segura-24"),null);
        assertEquals(200,login.statusCode());
        assertEquals(registered.path("user").path("accountId").asText(),mapper.readTree(login.body()).path("user").path("accountId").asText());
        var wrong=call("POST","/api/auth/login",Map.of("username","ana","password","incorrecta"),null);
        var absent=call("POST","/api/auth/login",Map.of("username","no_existe","password","incorrecta"),null);
        assertEquals(401,wrong.statusCode()); assertEquals(401,absent.statusCode()); assertEquals(wrong.body(),absent.body());
        assertEquals("Usuario o contraseña incorrectos",mapper.readTree(wrong.body()).path("message").asText());
        assertEquals(401,call("GET","/api/auth/me",null,null).statusCode());
    }
    @Test void duplicateUsernameDoesNotCreateAnExtraAccount() throws Exception {
        register("ana");
        assertEquals(409,call("POST","/api/auth/register",registration("ANA"),null).statusCode());
        assertEquals(1,users.count()); assertEquals(3,accounts.count()); assertEquals(1,sessions.count());
    }
    @Test void validatesRegistrationAndDoesNotPersistInvalidUsers() throws Exception {
        for(var body:List.of(Map.of("displayName","Ana","username","an","password","Prueba-segura-24"),
            Map.of("displayName","Ana","username","ana","password","123"),
            Map.of("displayName","Ana","username","ana@dominio","password","Prueba-segura-24"),
            Map.of("displayName","Ana","username","ana","password","á".repeat(40)))) {
            assertEquals(400,call("POST","/api/auth/register",body,null).statusCode());
        }
        assertEquals(0,users.count()); assertEquals(2,accounts.count());
    }
    @Test void usersCanTransferButCannotReadOrSpendAnotherAccount() throws Exception {
        var a=register("ana"); var b=register("bruno");
        String token=a.path("token").asText(), account=a.path("user").path("accountId").asText();
        String dest=b.path("user").path("accountId").asText();
        assertEquals(403,call("GET","/api/accounts/"+dest+"/snapshot",null,token).statusCode());
        assertEquals(403,call("POST","/api/transactions/deposit",Map.of("operationId",UUID.randomUUID(),"accountId",dest,"amount","5.00"),token).statusCode());
        assertEquals(200,call("POST","/api/transactions/deposit",Map.of("operationId",UUID.randomUUID(),"accountId",account,"amount","5.00"),token).statusCode());
        var request=Map.of("operationId",UUID.randomUUID().toString(),"accountId",account,"destinationAccountId",dest,"amount","2.00");
        assertEquals(200,call("POST","/api/transactions/transfer",request,token).statusCode());
        assertTrue(mapper.readTree(call("POST","/api/transactions/transfer",request,token).body()).path("replayed").asBoolean());
        assertEquals(3,entries.count());
        var recipient=mapper.readTree(call("GET","/api/accounts/"+dest+"/snapshot",null,b.path("token").asText()).body());
        assertEquals(0,new java.math.BigDecimal("2.00").compareTo(recipient.path("account").path("balance").decimalValue()));
    }
    @Test void logoutRevokesOnlyThatSessionAndClosesItsWebSocket() throws Exception {
        var auth=register("ana"); String token=auth.path("token").asText();
        var another=mapper.readTree(call("POST","/api/auth/login",Map.of("username","ana","password","Prueba-segura-24"),null).body());
        var closed=new CompletableFuture<Integer>();
        var connected=new CompletableFuture<Void>();
        var socket=http.newWebSocketBuilder().header("Authorization","Bearer "+token).buildAsync(
            URI.create("ws://127.0.0.1:"+port+"/ws"),new WebSocket.Listener() {
                public void onOpen(WebSocket ws) { ws.request(1); }
                public CompletionStage<?> onText(WebSocket ws,CharSequence text,boolean last) { connected.complete(null); ws.request(1); return null; }
                public void onError(WebSocket ws,Throwable error) { closed.completeExceptionally(error); }
                public CompletionStage<?> onClose(WebSocket ws,int status,String reason) { closed.complete(status); return null; }
            }).get(5,TimeUnit.SECONDS);
        connected.get(5,TimeUnit.SECONDS);
        assertEquals(204,call("POST","/api/auth/logout",null,token).statusCode());
        assertEquals(1008,closed.get(5,TimeUnit.SECONDS)); socket.abort();
        assertEquals(401,call("GET","/api/auth/me",null,token).statusCode());
        assertEquals(200,call("GET","/api/auth/me",null,another.path("token").asText()).statusCode());
    }
    @Test void expiredSessionIsRejected() throws Exception {
        var auth=register("ana"); var session=sessions.findAll().getFirst();
        session.expiresAt=Instant.now().minusSeconds(1); sessions.saveAndFlush(session);
        assertEquals(401,call("GET","/api/auth/me",null,auth.path("token").asText()).statusCode());
    }
    @Test void simultaneousRegistrationsKeepOneUsernameAndOneWallet() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var gate=new CountDownLatch(1);
            Callable<Integer> attempt=()-> { gate.await(); return call("POST","/api/auth/register",registration("ana"),null).statusCode(); };
            var a=pool.submit(attempt); var b=pool.submit(attempt); gate.countDown();
            var codes=new ArrayList<>(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))); Collections.sort(codes);
            assertEquals(List.of(201,409),codes); assertEquals(1,users.count()); assertEquals(3,accounts.count());
        } finally { pool.shutdownNow(); }
    }
}
