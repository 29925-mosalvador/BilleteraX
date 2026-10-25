package ec.edu.espe.billeterax;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.event.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static ec.edu.espe.billeterax.protocol.GatewayProtocol.*;

@Component
@EnableScheduling
@ConditionalOnProperty(name="wallet.gateway.enabled",havingValue="true")
public class GatewayAgent {
    private final GatewayCommandDispatcher dispatcher;
    private final ObjectMapper mapper;
    private final URI exchange;
    private final String token,nodeId;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final BlockingQueue<JsonNode> notices=new LinkedBlockingQueue<>(1024);
    private final AtomicBoolean reset=new AtomicBoolean();
    private final List<Result> results=new ArrayList<>();
    private final List<JsonNode> unacknowledged=new ArrayList<>();
    private boolean resetPending;
    GatewayAgent(GatewayCommandDispatcher dispatcher,ObjectMapper mapper,@Value("${wallet.gateway.url}") String url,@Value("${wallet.gateway.token}") String token,@Value("${wallet.gateway.node-id:debian-primary}") String nodeId,@Value("${wallet.gateway.allow-local-http:false}") boolean localHttp){
        URI base=URI.create(url);
        boolean local=localHttp && Set.of("127.0.0.1","localhost","[::1]").contains(base.getHost());
        if((!"https".equals(base.getScheme()) && !(local && "http".equals(base.getScheme()))) || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null || !Set.of("","/").contains(base.getPath()))throw new IllegalArgumentException("GATEWAY_URL requiere una dirección HTTPS sin rutas ni credenciales");
        if(token.length()<32)throw new IllegalArgumentException("Configura el secreto del servicio auxiliar");
        this.dispatcher=dispatcher;this.mapper=mapper;this.exchange=base.resolve("/agent/exchange");this.token=token;this.nodeId=nodeId;
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void committed(ApiModels.Committed batch){for(var event:batch.events())enqueue(mapper.valueToTree(Map.of("kind","EVENT","event",event)));}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void revoked(AuthModels.SessionRevoked event){enqueue(mapper.valueToTree(Map.of("kind","REVOKED","sessionHash",event.sessionHash())));}
    private void enqueue(JsonNode notice){if(!notices.offer(notice))reset.set(true);}
    @Scheduled(fixedDelayString="${wallet.gateway.poll-ms:750}",initialDelay=1000)
    public synchronized void exchange(){
        if(unacknowledged.isEmpty())notices.drainTo(unacknowledged,128);
        resetPending=resetPending || reset.getAndSet(false);
        try{
            var body=new Exchange(nodeId,List.copyOf(results),List.copyOf(unacknowledged),resetPending);
            var request=HttpRequest.newBuilder(exchange).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json").header("X-Billetera-Agent",token).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)return;
            var batch=mapper.readValue(response.body(),Batch.class);
            if(batch.commands()==null || batch.commands().size()>4)return;
            results.clear();unacknowledged.clear();resetPending=false;
            for(var command:batch.commands())results.add(dispatcher.dispatch(command));
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Exception ignored){/* Retry transport without logging credentials or queued request bodies. */}
    }
}
