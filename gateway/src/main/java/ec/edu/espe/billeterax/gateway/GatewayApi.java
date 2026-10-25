package ec.edu.espe.billeterax.gateway;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static ec.edu.espe.billeterax.protocol.GatewayProtocol.*;

@RestController
public class GatewayApi {
    private final MessageBroker broker;
    private final SocketHub sockets;
    private final ObjectMapper mapper;
    private final String agentToken,nodeId;
    GatewayApi(MessageBroker broker,SocketHub sockets,ObjectMapper mapper,@Value("${gateway.agent-token}") String token,@Value("${gateway.node-id}") String node) {
        if(token.length()<32)throw new IllegalStateException("Configura GATEWAY_AGENT_TOKEN con al menos 32 caracteres aleatorios");
        this.broker=broker;this.sockets=sockets;this.mapper=mapper;this.agentToken=token;this.nodeId=node;
    }
    private DeferredResult<ResponseEntity<JsonNode>> call(String kind,JsonNode body,String auth,boolean login) {
        if(!login && (auth==null || !auth.startsWith("Bearer ") || auth.length()>256)){
            var result=new DeferredResult<ResponseEntity<JsonNode>>();
            result.setResult(ResponseEntity.status(401).body(mapper.valueToTree(MessageBroker.error("UNAUTHORIZED","Inicia sesión para continuar"))));return result;
        }
        return broker.request(kind,body,auth);
    }
    @GetMapping(value="/config.js",produces="text/javascript") String config(){return "window.BILLETERAX_CONFIG={apiBase:window.location.origin,managed:true};";}
    @GetMapping("/api/status") Map<String,Object> status(){ return Map.of("online",broker.online(),"primary","Debian"); }
    @PostMapping("/agent/exchange") ResponseEntity<Batch> exchange(@RequestHeader(value="X-Billetera-Agent",defaultValue="") String token,@RequestBody Exchange message) {
        if(!MessageDigest.isEqual(agentToken.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)) || !nodeId.equals(message.nodeId()))return ResponseEntity.status(403).build();
        if((message.results()!=null && message.results().size()>8) || (message.notices()!=null && message.notices().size()>256))return ResponseEntity.badRequest().build();
        if(message.results()!=null && message.results().stream().anyMatch(r -> r.status()<200 || r.status()>599))return ResponseEntity.badRequest().build();
        // Revocations must reach the hub before a pending handshake may complete.
        sockets.notices(message.notices(),message.resetSockets());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(broker.exchange(message.results()));
    }
    @PostMapping("/api/auth/register") Object register(@RequestBody JsonNode body){return call("REGISTER",body,null,true);}
    @PostMapping("/api/auth/login") Object login(@RequestBody JsonNode body){return call("LOGIN",body,null,true);}
    @GetMapping("/api/auth/me") Object me(@RequestHeader(value="Authorization",required=false) String auth){return call("PROFILE",null,auth,false);}
    @PostMapping("/api/auth/logout") Object logout(@RequestHeader(value="Authorization",required=false) String auth){return call("LOGOUT",null,auth,false);}
    @PostMapping("/api/auth/ws-ticket") Object ticket(@RequestHeader(value="Authorization",required=false) String auth){return call("WS_TICKET",null,auth,false);}
    @GetMapping("/api/accounts/{id}/snapshot") Object snapshot(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String auth){return call("SNAPSHOT",mapper.valueToTree(Map.of("accountId",id)),auth,false);}
    @GetMapping("/api/accounts/{id}/balance") Object balance(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String auth){return call("BALANCE",mapper.valueToTree(Map.of("accountId",id)),auth,false);}
    @GetMapping("/api/accounts/{id}/transactions") Object history(@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestHeader(value="Authorization",required=false) String auth){return call("HISTORY",mapper.valueToTree(Map.of("accountId",id,"page",page,"size",size)),auth,false);}
    @PostMapping("/api/transactions/deposit") Object deposit(@RequestBody JsonNode body,@RequestHeader(value="Authorization",required=false) String auth){return call("DEPOSIT",body,auth,false);}
    @PostMapping("/api/transactions/withdraw") Object withdraw(@RequestBody JsonNode body,@RequestHeader(value="Authorization",required=false) String auth){return call("WITHDRAW",body,auth,false);}
    @PostMapping("/api/transactions/transfer") Object transfer(@RequestBody JsonNode body,@RequestHeader(value="Authorization",required=false) String auth){return call("TRANSFER",body,auth,false);}
    @PostMapping("/api/payments/services") Object service(@RequestBody JsonNode body,@RequestHeader(value="Authorization",required=false) String auth){return call("SERVICE",body,auth,false);}
}
