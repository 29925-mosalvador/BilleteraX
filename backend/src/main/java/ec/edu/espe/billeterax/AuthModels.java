package ec.edu.espe.billeterax;
import jakarta.validation.constraints.*;
import java.time.Instant;
public final class AuthModels {
    public record Registration(@NotBlank @Size(min=2,max=60) String displayName,
        @NotBlank @Size(min=3,max=30) String username,
        @NotNull @Size(min=8,max=72) String password) {}
    public record Login(@NotBlank @Size(max=30) String username,
                        @NotNull @Size(max=72) String password) {}
    public record Profile(String id,String username,String displayName,String accountId) {}
    public record Authentication(String token,Instant expiresAt,Profile user) {}
    public record Principal(String accountId,String sessionHash,Instant expiresAt) {}
    public record SessionRevoked(String sessionHash) {}
    private AuthModels() {}
}
