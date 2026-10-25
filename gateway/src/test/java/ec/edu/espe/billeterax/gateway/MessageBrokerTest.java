package ec.edu.espe.billeterax.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static ec.edu.espe.billeterax.protocol.GatewayProtocol.*;

class MessageBrokerTest {
    static class MutableClock extends Clock {
        Instant value=Instant.parse("2026-10-25T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return value;}
    }
    @Test void requiresRecentContactFromDebianBeforeAcceptingBusinessCommands() {
        var clock=new MutableClock();var broker=new MessageBroker(new ObjectMapper(),clock,6,25);
        assertEquals(503,broker.submit("LOGIN",null,null).join().status());assertEquals(0,broker.pendingCount());
        broker.exchange(List.of());assertTrue(broker.online());clock.value=clock.value.plusSeconds(6);assertFalse(broker.online());
        assertEquals(503,broker.submit("DEPOSIT",null,"Bearer session").join().status());
    }
    @Test void onlyDebianRepliesCanCompleteARequestAndDuplicateRepliesHaveNoSecondEffect() {
        var clock=new MutableClock();var broker=new MessageBroker(new ObjectMapper(),clock,6,25);broker.exchange(List.of());
        var future=broker.submit("SNAPSHOT",JsonNodeFactory.instance.objectNode().put("accountId","CTA001"),"Bearer session");
        var batch=broker.exchange(List.of());assertEquals(1,batch.commands().size());assertFalse(future.isDone());
        var command=batch.commands().getFirst();assertEquals("SNAPSHOT",command.kind());assertEquals("Bearer session",command.authorization());
        assertTrue(broker.exchange(List.of()).commands().isEmpty());
        var result=new Result(command.id(),200,JsonNodeFactory.instance.objectNode().put("balance","2.00"));
        broker.exchange(List.of(result));assertEquals(result,future.join());assertEquals(0,broker.pendingCount());
        broker.exchange(List.of(result));assertEquals(0,broker.pendingCount());
    }
    @Test void responseTimeoutIsReportedAsUncertainAndExpiredCommandsAreNotDelivered() {
        var clock=new MutableClock();var broker=new MessageBroker(new ObjectMapper(),clock,6,25);broker.exchange(List.of());
        var future=broker.submit("DEPOSIT",null,"Bearer session");clock.value=clock.value.plusSeconds(26);broker.expire();
        assertEquals(504,future.join().status());assertEquals("RESULT_UNKNOWN",future.join().body().path("code").asText());assertEquals(0,broker.pendingCount());
        assertTrue(broker.exchange(List.of()).commands().isEmpty());
    }
    @Test void queueHasFiniteCapacityAndMessagesAreDeliveredInSmallBatches() {
        var clock=new MutableClock();var broker=new MessageBroker(new ObjectMapper(),clock,6,25);broker.exchange(List.of());
        for(int i=0;i<64;i++)assertFalse(broker.submit("PROFILE",null,"Bearer session").isDone());
        assertEquals(503,broker.submit("PROFILE",null,"Bearer session").join().status());assertEquals(64,broker.pendingCount());
        assertEquals(4,broker.exchange(List.of()).commands().size());
    }
}
