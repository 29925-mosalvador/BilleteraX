package ec.edu.espe.billeterax;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ec.edu.espe.billeterax.protocol.GatewayProtocol.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:gateway-dispatch;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop"})
class GatewayCommandDispatcherTest {
    @Autowired GatewayCommandDispatcher dispatch;
    @Autowired ObjectMapper mapper;
    Command command(String kind,Object body,String token){return new Command(UUID.randomUUID(),kind,mapper.valueToTree(body),token==null?null:"Bearer "+token,Instant.now().plusSeconds(30));}
    Result register(){return dispatch.dispatch(command("REGISTER",Map.of("username","node"+UUID.randomUUID().toString().replace("-","").substring(0,12),"displayName","Usuario mensajes","password","Prueba-mensajes-123"),null));}
    @Test void businessMessagesUseTheExistingAccountAuthorizationAndIdempotency(){
        var a=register();var b=register();assertEquals(201,a.status());assertEquals(201,b.status());
        String token=a.body().path("token").asText(),account=a.body().path("user").path("accountId").asText(),other=b.body().path("user").path("accountId").asText();
        assertEquals(403,dispatch.dispatch(command("SNAPSHOT",Map.of("accountId",other),token)).status());
        var deposit=Map.of("operationId",UUID.randomUUID().toString(),"accountId",account,"amount","3.00");
        assertEquals(200,dispatch.dispatch(command("DEPOSIT",deposit,token)).status());assertTrue(dispatch.dispatch(command("DEPOSIT",deposit,token)).body().path("replayed").asBoolean());
        var transfer=Map.of("operationId",UUID.randomUUID().toString(),"accountId",account,"destinationAccountId",other,"amount","1.00");assertEquals(200,dispatch.dispatch(command("TRANSFER",transfer,token)).status());
        assertEquals(0,new java.math.BigDecimal("2.00").compareTo(dispatch.dispatch(command("SNAPSHOT",Map.of("accountId",account),token)).body().path("account").path("balance").decimalValue()));
        assertEquals(204,dispatch.dispatch(command("LOGOUT",null,token)).status());assertEquals(401,dispatch.dispatch(command("PROFILE",null,token)).status());
    }
    @Test void rejectedAndExpiredMessagesCannotExecuteArbitraryActions(){
        var a=register();String token=a.body().path("token").asText(),account=a.body().path("user").path("accountId").asText();
        assertEquals(400,dispatch.dispatch(command("EXEC",Map.of("url","http://router/","command","shell"),token)).status());
        var expired=new Command(UUID.randomUUID(),"DEPOSIT",mapper.valueToTree(Map.of("operationId",UUID.randomUUID().toString(),"accountId",account,"amount","99.00")),"Bearer "+token,Instant.now().minusSeconds(1));
        assertEquals(408,dispatch.dispatch(expired).status());
        assertEquals(400,dispatch.dispatch(command("DEPOSIT",Map.of("accountId",account,"amount","1.001"),token)).status());
        assertEquals(0,dispatch.dispatch(command("BALANCE",Map.of("accountId",account),token)).body().path("balance").decimalValue().signum());
    }
    @Test void browserTicketRemainsSingleUseAcrossTheMessageBoundary(){
        var a=register();String token=a.body().path("token").asText();
        var ticket=dispatch.dispatch(command("WS_TICKET",null,token));assertEquals(200,ticket.status());
        var body=Map.of("ticket",ticket.body().path("ticket").asText());
        assertEquals(200,dispatch.dispatch(command("CONSUME_TICKET",body,null)).status());assertEquals(401,dispatch.dispatch(command("CONSUME_TICKET",body,null)).status());
    }
}
