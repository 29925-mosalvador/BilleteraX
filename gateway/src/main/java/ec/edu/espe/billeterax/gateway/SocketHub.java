package ec.edu.espe.billeterax.gateway;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocketHub extends TextWebSocketHandler {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(SocketHub.class);
    private final Map<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
    private final Map<String,Instant> revoked=new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final MessageBroker broker;
    SocketHub(ObjectMapper mapper,MessageBroker broker){this.mapper=mapper;this.broker=broker;}
    private boolean valid(WebSocketSession session){
        Object hash=session.getAttributes().get("sessionHash"),expires=session.getAttributes().get("expiresAt");
        return broker.online() && !(hash instanceof String h && revoked.containsKey(h)) && !(expires instanceof Instant time && !time.isAfter(Instant.now()));
    }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        var safe=new ConcurrentWebSocketSessionDecorator(session,2000,65536);
        if(sessions.size()>=128 || !valid(safe)){safe.close(CloseStatus.POLICY_VIOLATION);return;}
        sessions.put(session.getId(),safe);
        if(!valid(safe)){close(safe,CloseStatus.POLICY_VIOLATION);return;}
        safe.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type","CONNECTED","accountId",safe.getAttributes().get("accountId")))));
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){sessions.remove(session.getId());}
    @Override public void handleTransportError(WebSocketSession session,Throwable error){close(session,CloseStatus.SERVER_ERROR);}
    private void close(WebSocketSession session,CloseStatus reason){
        sessions.remove(session.getId());
        // An explicit notice lets clients close even if an intermediary delays the close frame.
        String type=reason.getCode()==1008?"SESSION_REVOKED":"SERVER_UNAVAILABLE";
        try{if(session.isOpen())session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type",type,"accountId",session.getAttributes().get("accountId")))));}
        catch(Exception error){log.warn("No se pudo enviar aviso de cierre: {}",error.getClass().getSimpleName());}
        try{session.close(reason);log.info("Cierre WebSocket: codigo={}, abierto={}",reason.getCode(),session.isOpen());}
        catch(Exception error){log.warn("No se pudo cerrar WebSocket: {}",error.getClass().getSimpleName());}
    }
    void notices(List<JsonNode> notices,boolean reset){
        if(reset)for(var session:sessions.values())close(session,CloseStatus.SERVICE_RESTARTED);
        if(notices==null)return;
        for(var notice:notices)if("REVOKED".equals(notice.path("kind").asText())){
            String hash=notice.path("sessionHash").asText();if(hash.length()!=64)continue;
            revoked.put(hash,Instant.now().plusSeconds(60));
            log.info("Revocación recibida: conexiones coincidentes={}",sessions.values().stream().filter(s -> hash.equals(s.getAttributes().get("sessionHash"))).count());
            for(var session:sessions.values())if(hash.equals(session.getAttributes().get("sessionHash")))close(session,CloseStatus.POLICY_VIOLATION);
        }
        for(var notice:notices)if("EVENT".equals(notice.path("kind").asText())){
            JsonNode event=notice.path("event");String account=event.path("accountId").asText();
            for(var session:sessions.values()){
                if(!valid(session)){close(session,CloseStatus.POLICY_VIOLATION);continue;}
                if(!account.equals(session.getAttributes().get("accountId")))continue;
                try{if(session.isOpen())session.sendMessage(new TextMessage(mapper.writeValueAsString(event)));}catch(Exception error){close(session,CloseStatus.SERVER_ERROR);}
            }
        }
    }
    @Scheduled(fixedDelay=1000) void checkAvailability(){
        revoked.entrySet().removeIf(e -> !e.getValue().isAfter(Instant.now()));
        for(var session:sessions.values())if(!valid(session))close(session,CloseStatus.SERVICE_RESTARTED);
    }
}
