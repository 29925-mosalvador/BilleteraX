package ec.edu.espe.billeterax;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.event.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived, single-use browser handshakes. No session bearer token in a URL. */
@Service
public class SocketTickets {
    record Ticket(String ticket,Instant expiresAt) {}
    private record Grant(AuthModels.Principal principal,Instant expiresAt) {}
    private final Map<String,Grant> grants=new ConcurrentHashMap<>();
    private final UserSessions sessions;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    @Autowired SocketTickets(UserSessions sessions) { this(sessions,Clock.systemUTC()); }
    SocketTickets(UserSessions sessions,Clock clock) { this.sessions=sessions; this.clock=clock; }
    synchronized Ticket issue(AuthModels.Principal principal) {
        if(principal==null || principal.sessionHash()==null || !principal.expiresAt().isAfter(clock.instant()))
            throw new ApiException(401,"UNAUTHORIZED","Inicia sesión para continuar");
        grants.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(clock.instant()));
        if(grants.size()>=10000) throw new ApiException(429,"TRY_LATER","Reintenta la conexión en unos segundos");
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String ticket=HexFormat.of().formatHex(bytes);
        Instant expires=clock.instant().plusSeconds(30);
        if(principal.expiresAt().isBefore(expires)) expires=principal.expiresAt();
        grants.put(ticket,new Grant(principal,expires));
        return new Ticket(ticket,expires);
    }
    AuthModels.Principal consume(String ticket) {
        if(ticket==null || !ticket.matches("[a-f0-9]{64}")) return null;
        Grant grant=grants.remove(ticket); // Atomic: only one handshake may consume this ticket.
        if(grant==null || !grant.expiresAt().isAfter(clock.instant())) return null;
        var principal=grant.principal();
        return sessions.existsByTokenHashAndExpiresAtAfter(principal.sessionHash(),clock.instant()) ? principal : null;
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void revoked(AuthModels.SessionRevoked event) {
        grants.entrySet().removeIf(e -> e.getValue().principal().sessionHash().equals(event.sessionHash()));
    }
}
