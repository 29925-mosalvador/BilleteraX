package ec.edu.espe.billeterax;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import static ec.edu.espe.billeterax.AuthModels.*;

@Service
public class UserAuthService {
    private final Users users;
    private final UserSessions sessions;
    private final Accounts accounts;
    private final ApplicationEventPublisher publisher;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(10);
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash = passwords.encode(UUID.randomUUID().toString());
    UserAuthService(Users users,UserSessions sessions,Accounts accounts,ApplicationEventPublisher publisher) {
        this.users=users; this.sessions=sessions; this.accounts=accounts; this.publisher=publisher;
    }
    private String normalize(String username) { return username.trim().toLowerCase(Locale.ROOT); }
    @Transactional
    public Authentication register(Registration registration) {
        String username=normalize(registration.username());
        String name=registration.displayName().trim();
        if(!username.matches("[a-z0-9][a-z0-9._-]{2,29}") || name.length()<2 || name.length()>60 || name.chars().anyMatch(Character::isISOControl))
            throw new ApiException(400,"INVALID_REGISTRATION","Nombre de 2 a 60 caracteres; usuario de 3 a 30 letras, números, punto, guion o guion bajo");
        if(registration.password().getBytes(StandardCharsets.UTF_8).length>72)
            throw new ApiException(400,"INVALID_PASSWORD","La contraseña admite un máximo de 72 bytes");
        if(users.existsByUsername(username)) throw new ApiException(409,"USERNAME_TAKEN","Ese nombre de usuario ya está en uso");
        String accountId;
        do { accountId="CTA"+String.format(Locale.ROOT,"%010d",random.nextLong(10_000_000_000L)); }
        while(accounts.existsById(accountId));
        Account account=accounts.save(new Account(accountId,name,"0.00"));
        WalletUser user=new WalletUser(); user.id=UUID.randomUUID().toString();
        user.username=username; user.displayName=name; user.passwordHash=passwords.encode(registration.password());
        user.createdAt=Instant.now(); user.account=account;
        users.saveAndFlush(user);
        return createSession(user);
    }
    @Transactional
    public Authentication login(Login login) {
        WalletUser user=users.findByUsername(normalize(login.username())).orElse(null);
        boolean fits=login.password().getBytes(StandardCharsets.UTF_8).length<=72;
        boolean matches=passwords.matches(fits ? login.password() : "",user==null ? dummyHash : user.passwordHash);
        if(user==null || !fits || !matches) throw new ApiException(401,"INVALID_LOGIN","Usuario o contraseña incorrectos");
        return createSession(user);
    }
    private Authentication createSession(WalletUser user) {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=HexFormat.of().formatHex(bytes);
        WalletSession session=new WalletSession(); session.tokenHash=hash(token); session.user=user;
        session.createdAt=Instant.now(); session.expiresAt=session.createdAt.plus(Duration.ofDays(30));
        sessions.save(session);
        return new Authentication(token,session.expiresAt,profileOf(user));
    }
    @Transactional(readOnly=true)
    public Principal authenticate(String token) {
        if(token==null || !token.matches("[a-f0-9]{64}")) return null;
        WalletSession session=sessions.authenticated(hash(token)).orElse(null);
        return session==null || !session.expiresAt.isAfter(Instant.now()) ? null :
            new Principal(session.user.account.id,session.tokenHash,session.expiresAt);
    }
    @Transactional(readOnly=true)
    public Profile profile(String accountId) {
        return profileOf(users.findByAccountId(accountId).orElseThrow(() ->
            new ApiException(404,"USER_NOT_FOUND","Esta cuenta de laboratorio no tiene un usuario registrado")));
    }
    @Transactional
    public void logout(String header) {
        if(header==null || !header.startsWith("Bearer ")) return;
        String hash=hash(header.substring(7));
        sessions.findById(hash).ifPresent(session -> {
            sessions.delete(session); publisher.publishEvent(new SessionRevoked(hash));
        });
    }
    private Profile profileOf(WalletUser user) { return new Profile(user.id,user.username,user.displayName,user.account.id); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
