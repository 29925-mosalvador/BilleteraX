package ec.edu.espe.billeterax;


import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;
import static ec.edu.espe.billeterax.ApiModels.*;
@Component
public class WalletSocket extends TextWebSocketHandler {
    private final ConcurrentHashMap<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final UserSessions authenticationSessions;
    WalletSocket(ObjectMapper mapper,UserSessions authenticationSessions) { this.mapper=mapper; this.authenticationSessions=authenticationSessions; }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        WebSocketSession safe=new ConcurrentWebSocketSessionDecorator(session,2000,65536);
        sessions.put(session.getId(),safe);
        Object hash=session.getAttributes().get("sessionHash");
        // Logout can race the interval between HTTP handshake and socket registration.
        if(hash instanceof String value && !authenticationSessions.existsByTokenHashAndExpiresAtAfter(value,java.time.Instant.now())) {
            sessions.remove(session.getId()); safe.close(CloseStatus.POLICY_VIOLATION); return;
        }
        if(safe.isOpen()) safe.sendMessage(new TextMessage(mapper.writeValueAsString(java.util.Map.of("type","CONNECTED","accountId",session.getAttributes().get("accountId")))));
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) { sessions.remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session,Throwable exception) throws Exception {
        sessions.remove(session.getId()); session.close(CloseStatus.SERVER_ERROR);
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void revoked(AuthModels.SessionRevoked event) {
        for(WebSocketSession session:sessions.values()) {
            if(event.sessionHash().equals(session.getAttributes().get("sessionHash"))) {
                sessions.remove(session.getId());
                try { session.close(CloseStatus.POLICY_VIOLATION); } catch(Exception ignored) {}
            }
        }
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void committed(Committed batch) {
        // Failure to deliver an event must never turn a committed payment into an HTTP failure.
        for(Event event:batch.events()) for(WebSocketSession session:sessions.values()) {
            if(!event.accountId().equals(session.getAttributes().get("accountId"))) continue;
            Object expiry=session.getAttributes().get("expiresAt");
            if(expiry instanceof java.time.Instant instant && !instant.isAfter(java.time.Instant.now())) {
                sessions.remove(session.getId());
                try { session.close(CloseStatus.POLICY_VIOLATION); } catch(Exception ignored) {}
                continue;
            }
            try { if(session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(event))); }
            catch(Exception e) {
                org.slf4j.LoggerFactory.getLogger(WalletSocket.class).warn("WebSocket delivery failed; REST reconciliation is available",e);
                sessions.remove(session.getId()); try { session.close(CloseStatus.SERVER_ERROR); } catch(Exception ignored) {}
            }
        }
    }
}
