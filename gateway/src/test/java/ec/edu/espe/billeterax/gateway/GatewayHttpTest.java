package ec.edu.espe.billeterax.gateway;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static ec.edu.espe.billeterax.protocol.GatewayProtocol.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"gateway.agent-token=local-test-agent-key-not-a-real-secret-123456","gateway.offline-seconds=60"})
class GatewayHttpTest {
    static final String KEY="local-test-agent-key-not-a-real-secret-123456",ORIGIN="https://29925-mosalvador.github.io";
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    final HttpClient http=HttpClient.newHttpClient();
    HttpResponse<String> call(String method,String path,Object body,Map<String,String> headers)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));headers.forEach(b::header);
        if(body!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode exchange(List<Result> results,List<JsonNode> notices)throws Exception{
        var r=call("POST","/agent/exchange",new Exchange("debian-primary",results,notices,false),Map.of("X-Billetera-Agent",KEY));assertEquals(200,r.statusCode(),r.body());return mapper.readTree(r.body());
    }
    Command nextCommand()throws Exception{
        for(int i=0;i<100;i++){
            var batch=mapper.treeToValue(exchange(List.of(),List.of()),Batch.class);
            if(!batch.commands().isEmpty())return batch.commands().getFirst();Thread.sleep(20);
        }
        throw new AssertionError("No se recibió el mensaje esperado");
    }
    @Test void protectsWorkerAccessAndPreservesCorsAndManagedWebConfiguration()throws Exception{
        assertEquals(403,call("POST","/agent/exchange",Map.of(),Map.of()).statusCode());
        assertEquals(403,call("POST","/agent/exchange",new Exchange("other-node",List.of(),List.of(),false),Map.of("X-Billetera-Agent",KEY)).statusCode());
        assertEquals(401,call("GET","/api/auth/me",null,Map.of()).statusCode());
        assertEquals(403,call("POST","/api/auth/login",Map.of(),Map.of("Origin","https://untrusted.example")).statusCode());
        var preflight=call("OPTIONS","/api/auth/login",null,Map.of("Origin",ORIGIN,"Access-Control-Request-Method","POST","Access-Control-Request-Headers","content-type"));
        assertEquals(200,preflight.statusCode());assertEquals(ORIGIN,preflight.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(call("GET","/config.js",null,Map.of()).body().contains("managed:true"));
        assertTrue(call("GET","/",null,Map.of()).body().contains("BilleteraX · Banca web"));
        assertEquals(413,call("POST","/api/auth/login",Map.of("password","x".repeat(33000)),Map.of()).statusCode());
    }
    @Test void publicRestWaitsForDebianAndWebSocketsDeliverOnlyTheAuthorizedAccount()throws Exception{
        exchange(List.of(),List.of());
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/login")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"ana\",\"password\":\"test-only\"}")).build();
        var response=http.sendAsync(req,HttpResponse.BodyHandlers.ofString());var command=nextCommand();
        assertEquals("LOGIN",command.kind());assertFalse(response.isDone());
        exchange(List.of(new Result(command.id(),200,mapper.valueToTree(Map.of("token","opaque-test-session")))),List.of());
        assertEquals(200,response.get(5,TimeUnit.SECONDS).statusCode());
        var connected=new CompletableFuture<String>();var incoming=new CompletableFuture<String>();var closed=new CompletableFuture<Integer>();var revokedNotice=new CompletableFuture<String>();
        var connecting=http.newWebSocketBuilder().header("Origin",ORIGIN).header("X-Forwarded-Proto","https").header("X-Forwarded-Host","wallet.example").header("Authorization","Bearer opaque-test-session").buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws"),new WebSocket.Listener(){
            public void onOpen(WebSocket socket){socket.request(1);}
            public CompletionStage<?> onText(WebSocket socket,CharSequence text,boolean last){if(text.toString().contains("CONNECTED"))connected.complete(text.toString());else incoming.complete(text.toString());if(text.toString().contains("SESSION_REVOKED"))revokedNotice.complete(text.toString());socket.request(1);return null;}
            public CompletionStage<?> onClose(WebSocket socket,int code,String reason){closed.complete(code);return null;}
        });
        var auth=nextCommand();assertEquals("AUTH_SOCKET",auth.kind());
        String hash="a".repeat(64);var identity=mapper.valueToTree(Map.of("accountId","CTA001","sessionHash",hash,"expiresAt",Instant.now().plusSeconds(60).toString()));
        exchange(List.of(new Result(auth.id(),200,identity)),List.of());var ws=connecting.get(5,TimeUnit.SECONDS);
        try{
            assertTrue(connected.get(5,TimeUnit.SECONDS).contains("CTA001"));
            exchange(List.of(),List.of(mapper.valueToTree(Map.of("kind","EVENT","event",Map.of("type","TRANSFER_RECEIVED","accountId","CTA002")))));assertFalse(incoming.isDone());
            exchange(List.of(),List.of(mapper.valueToTree(Map.of("kind","EVENT","event",Map.of("type","TRANSFER_RECEIVED","accountId","CTA001")))));assertTrue(incoming.get(5,TimeUnit.SECONDS).contains("CTA001"));
            exchange(List.of(),List.of(mapper.valueToTree(Map.of("kind","REVOKED","sessionHash",hash))));assertTrue(revokedNotice.get(5,TimeUnit.SECONDS).contains("CTA001"));assertEquals(1008,closed.get(5,TimeUnit.SECONDS));
        }finally{ws.abort();}
    }
}
