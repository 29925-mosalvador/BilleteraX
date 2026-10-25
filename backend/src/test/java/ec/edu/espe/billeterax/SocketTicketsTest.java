package ec.edu.espe.billeterax;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocketTicketsTest {
    @Test void expiryAndConcurrentConsumptionAreEnforced() throws Exception {
        var sessions=mock(UserSessions.class);when(sessions.existsByTokenHashAndExpiresAtAfter(anyString(),any())).thenReturn(true);
        Instant now=Instant.parse("2026-10-24T12:00:00Z");
        var principal=new AuthModels.Principal("CTA123","hash",now.plusSeconds(100));
        var clock=mock(Clock.class);when(clock.instant()).thenReturn(now);
        var tickets=new SocketTickets(sessions,clock);var expired=tickets.issue(principal);
        when(clock.instant()).thenReturn(now.plusSeconds(30));assertNull(tickets.consume(expired.ticket()));
        var ticket=tickets.issue(principal);var pool=Executors.newFixedThreadPool(2);
        try{var gate=new CountDownLatch(1);Callable<AuthModels.Principal> call=()->{gate.await();return tickets.consume(ticket.ticket());};var a=pool.submit(call);var b=pool.submit(call);gate.countDown();assertEquals(1,java.util.stream.Stream.of(a.get(),b.get()).filter(Objects::nonNull).count());}finally{pool.shutdownNow();}
    }
    @Test void databaseRevocationAndExpiredSessionsCannotBeBypassed() {
        var sessions=mock(UserSessions.class);var tickets=new SocketTickets(sessions);var principal=new AuthModels.Principal("CTA123","hash",Instant.now().plusSeconds(100));
        assertNull(tickets.consume(tickets.issue(principal).ticket()));
        assertThrows(ApiException.class,()->tickets.issue(new AuthModels.Principal("CTA123",null,null)));
        assertThrows(ApiException.class,()->tickets.issue(new AuthModels.Principal("CTA123","hash",Instant.now().minusSeconds(1))));
    }
}
