package ec.edu.espe.billeterax.gateway;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.context.request.async.DeferredResult;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static ec.edu.espe.billeterax.protocol.GatewayProtocol.*;

/** Bounded, volatile inbox. Only Debian executes business commands and owns persistent data. */
@Component
public class MessageBroker {
    private record Pending(Command command,CompletableFuture<Result> result) {}
    private final ConcurrentHashMap<UUID,Pending> pending=new ConcurrentHashMap<>();
    private final Queue<UUID> waiting=new ConcurrentLinkedQueue<>();
    private final AtomicReference<Instant> seen=new AtomicReference<>(Instant.MIN);
    private final Clock clock;
    private final ObjectMapper mapper;
    private final long offlineSeconds,requestSeconds;
    @org.springframework.beans.factory.annotation.Autowired
    MessageBroker(ObjectMapper mapper,@Value("${gateway.offline-seconds:6}") long offline,@Value("${gateway.request-seconds:25}") long request) {
        this(mapper,Clock.systemUTC(),offline,request);
    }
    MessageBroker(ObjectMapper mapper,Clock clock,long offline,long request) {
        this.mapper=mapper;this.clock=clock;this.offlineSeconds=offline;this.requestSeconds=request;
    }
    boolean online() { return seen.get().plusSeconds(offlineSeconds).isAfter(clock.instant()); }
    static Map<String,String> error(String code,String message) { return Map.of("code",code,"message",message); }
    private JsonNode json(Object value) { return mapper.valueToTree(value); }
    synchronized CompletableFuture<Result> submit(String kind,JsonNode body,String authorization) {
        if(!online())return CompletableFuture.completedFuture(new Result(null,503,json(error("DEBIAN_OFFLINE","El servidor Debian está apagado o sin conexión. Intenta más tarde."))));
        if(pending.size()>=64)return CompletableFuture.completedFuture(new Result(null,503,json(error("SERVER_BUSY","Hay varias solicitudes en curso. Reintenta en unos segundos."))));
        var command=new Command(UUID.randomUUID(),kind,body,authorization,clock.instant().plusSeconds(requestSeconds));
        var future=new CompletableFuture<Result>();pending.put(command.id(),new Pending(command,future));waiting.add(command.id());return future;
    }
    DeferredResult<ResponseEntity<JsonNode>> request(String kind,JsonNode body,String authorization) {
        var output=new DeferredResult<ResponseEntity<JsonNode>>((requestSeconds+2)*1000);
        submit(kind,body,authorization).thenAccept(result -> output.setResult(ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore()).body(result.status()==204?null:result.body())));
        return output;
    }
    Result identity(String kind,JsonNode body,String authorization) throws Exception { return submit(kind,body,authorization).get(8,TimeUnit.SECONDS); }
    synchronized Batch exchange(List<Result> results) {
        seen.set(clock.instant());
        if(results!=null)for(Result result:results){
            if(result.id()==null)continue;
            Pending item=pending.remove(result.id());
            if(item!=null)item.result().complete(result);
        }
        List<Command> commands=new ArrayList<>();
        UUID id;
        while(commands.size()<4 && (id=waiting.poll())!=null){
            Pending item=pending.get(id);
            if(item!=null && item.command().deadline().isAfter(clock.instant()))commands.add(item.command());
        }
        return new Batch(commands);
    }
    @Scheduled(fixedDelay=500)
    void expire() {
        for(var entry:pending.entrySet())if(!entry.getValue().command().deadline().isAfter(clock.instant()) && pending.remove(entry.getKey(),entry.getValue())){
            waiting.remove(entry.getKey());
            entry.getValue().result().complete(new Result(entry.getKey(),504,json(error("RESULT_UNKNOWN","No se pudo confirmar la respuesta de Debian. Reintenta la misma operación para comprobarla."))));
        }
    }
    int pendingCount() { return pending.size(); }
}
