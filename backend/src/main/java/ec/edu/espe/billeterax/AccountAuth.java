package ec.edu.espe.billeterax;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Component
public class AccountAuth {
    private final UserAuthService users;
    AccountAuth(UserAuthService users) { this.users=users; }
    @Value("${wallet.token-a}") String tokenA;
    @Value("${wallet.token-b}") String tokenB;
    @PostConstruct void validate() {
        if (tokenA.isBlank() && tokenB.isBlank()) return;
        if (tokenA.length()<20 || tokenB.length()<20 || tokenA.equals(tokenB))
            throw new IllegalStateException("Configure WALLET_TOKEN_A y WALLET_TOKEN_B diferentes, con al menos 20 caracteres");
    }
    public String authenticate(String header) {
        AuthModels.Principal principal=principal(header);
        return principal==null ? null : principal.accountId();
    }
    public AuthModels.Principal principal(String header) {
        if(header==null || !header.startsWith("Bearer ")) return null;
        byte[] candidate=header.substring(7).getBytes(StandardCharsets.UTF_8);
        if(!tokenA.isBlank() && MessageDigest.isEqual(candidate,tokenA.getBytes(StandardCharsets.UTF_8))) return new AuthModels.Principal("CTA001",null,null);
        if(!tokenB.isBlank() && MessageDigest.isEqual(candidate,tokenB.getBytes(StandardCharsets.UTF_8))) return new AuthModels.Principal("CTA002",null,null);
        return users.authenticate(header.substring(7));
    }
}
