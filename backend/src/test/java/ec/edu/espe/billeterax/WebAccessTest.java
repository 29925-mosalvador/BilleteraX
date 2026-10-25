package ec.edu.espe.billeterax;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:web-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "wallet.web-origins=https://29925-mosalvador.github.io,http://127.0.0.1:5173"})
class WebAccessTest {
    static final String ORIGIN="https://29925-mosalvador.github.io";
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    final HttpClient http=HttpClient.newHttpClient();
    HttpResponse<String> call(String method,String path,Object body,String token,String origin) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(origin!=null)b.header("Origin",origin);
        if(body!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode register() throws Exception {
        var r=call("POST","/api/auth/register",Map.of("displayName","Usuario Web","username","web"+UUID.randomUUID().toString().replace("-","").substring(0,20),"password","Prueba-web-segura"),null,ORIGIN);
        assertEquals(201,r.statusCode(),r.body());return mapper.readTree(r.body());
    }
    String ticket(String token) throws Exception {
        var r=call("POST","/api/auth/ws-ticket",null,token,ORIGIN);assertEquals(200,r.statusCode(),r.body());return mapper.readTree(r.body()).path("ticket").asText();
    }
    WebSocket socket(String ticket,String origin,WebSocket.Listener listener) throws Exception {
        return http.newWebSocketBuilder().header("Origin",origin).buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws?ticket="+ticket),listener).get(5,TimeUnit.SECONDS);
    }
    @Test void servesWebAndDoesNotCacheAccountResponses() throws Exception {
        var page=call("GET","/",null,null,null);assertEquals(200,page.statusCode());assertTrue(page.body().contains("BilleteraX · Banca web"));
        var a=register();var r=call("GET","/api/auth/me",null,a.path("token").asText(),ORIGIN);
        assertEquals("no-store",r.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("no-referrer",r.headers().firstValue("Referrer-Policy").orElseThrow());
        assertEquals(ORIGIN,r.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    }
    @Test void preflightPrecedesAuthenticationAndUnlistedOriginsAreRejected() throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/login")).header("Origin",ORIGIN).header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type").method("OPTIONS",HttpRequest.BodyPublishers.noBody());
        var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,r.statusCode());assertEquals(ORIGIN,r.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(r.headers().firstValue("Access-Control-Allow-Credentials").isEmpty());
        assertEquals(403,call("POST","/api/auth/login",Map.of("username","ana","password","wrong"),null,"https://untrusted.example").statusCode());
        assertEquals(401,call("GET","/api/auth/me",null,null,ORIGIN).statusCode());
    }
    @Test void ticketsRequireSessionAndCannotAuthenticateRestOrBeReused() throws Exception {
        assertEquals(401,call("POST","/api/auth/ws-ticket",null,null,ORIGIN).statusCode());
        var a=register();String token=a.path("token").asText(),ticket=ticket(token);
        assertEquals(401,call("GET","/api/auth/me",null,ticket,ORIGIN).statusCode());
        var connected=new CompletableFuture<String>();
        var ws=socket(ticket,ORIGIN,new WebSocket.Listener(){public void onOpen(WebSocket w){w.request(1);}public CompletionStage<?> onText(WebSocket w,CharSequence t,boolean last){connected.complete(t.toString());w.request(1);return null;}});
        try{assertTrue(connected.get(5,TimeUnit.SECONDS).contains(a.path("user").path("accountId").asText()));assertThrows(ExecutionException.class,()->socket(ticket,ORIGIN,new WebSocket.Listener(){}));}finally{ws.abort();}
    }
    @Test void logoutRevokesAnUnusedTicket() throws Exception {
        var a=register();String token=a.path("token").asText(),ticket=ticket(token);
        assertEquals(204,call("POST","/api/auth/logout",null,token,ORIGIN).statusCode());
        assertThrows(ExecutionException.class,()->socket(ticket,ORIGIN,new WebSocket.Listener(){}));
    }
    @Test void rejectsUntrustedWebSocketOriginWithoutBurningTicket() throws Exception {
        var a=register();String ticket=ticket(a.path("token").asText());
        assertThrows(ExecutionException.class,()->socket(ticket,"https://untrusted.example",new WebSocket.Listener(){}));
        var ws=socket(ticket,ORIGIN,new WebSocket.Listener(){});ws.abort();
    }
    @Test void browserSocketReceivesTransferFromAnotherSessionAndReconcilesSnapshot() throws Exception {
        var a=register();var b=register();String at=a.path("token").asText(),bt=b.path("token").asText();
        String aa=a.path("user").path("accountId").asText(),ba=b.path("user").path("accountId").asText();
        var connected=new CompletableFuture<Void>();var event=new CompletableFuture<JsonNode>();
        var ws=socket(ticket(at),ORIGIN,new WebSocket.Listener(){public void onOpen(WebSocket w){w.request(1);}public CompletionStage<?> onText(WebSocket w,CharSequence t,boolean last){try{var n=mapper.readTree(t.toString());if(n.path("type").asText().equals("CONNECTED"))connected.complete(null);if(n.path("type").asText().equals("TRANSFER_RECEIVED"))event.complete(n);}catch(Exception e){event.completeExceptionally(e);}w.request(1);return null;}});
        try{
            connected.get(5,TimeUnit.SECONDS);
            assertEquals(200,call("POST","/api/transactions/deposit",Map.of("operationId",UUID.randomUUID(),"accountId",ba,"amount","7.00"),bt,ORIGIN).statusCode());
            var transfer=Map.of("operationId",UUID.randomUUID().toString(),"accountId",ba,"amount","2.50","destinationAccountId",aa);
            assertEquals(200,call("POST","/api/transactions/transfer",transfer,bt,ORIGIN).statusCode());
            assertEquals(aa,event.get(5,TimeUnit.SECONDS).path("accountId").asText());
            var snapshot=mapper.readTree(call("GET","/api/accounts/"+aa+"/snapshot",null,at,ORIGIN).body());
            assertEquals(0,new java.math.BigDecimal("2.50").compareTo(snapshot.path("account").path("balance").decimalValue()));
            assertEquals(1,snapshot.path("transactions").size());
            assertEquals(403,call("GET","/api/accounts/"+ba+"/snapshot",null,at,ORIGIN).statusCode());
        }finally{ws.abort();}
    }
}
