package ec.edu.espe.billeterax.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Business messages only: no remote URLs, ports, shell commands or arbitrary HTTP forwarding. */
public final class GatewayProtocol {
    public record Command(UUID id,String kind,JsonNode body,String authorization,Instant deadline) {}
    public record Result(UUID id,int status,JsonNode body) {}
    public record Exchange(String nodeId,List<Result> results,List<JsonNode> notices,boolean resetSockets) {}
    public record Batch(List<Command> commands) {}
    private GatewayProtocol() {}
}
