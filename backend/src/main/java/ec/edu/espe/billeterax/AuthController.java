package ec.edu.espe.billeterax;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static ec.edu.espe.billeterax.AuthModels.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAuthService auth;
    AuthController(UserAuthService auth) { this.auth=auth; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    Authentication register(@Valid @RequestBody Registration request) { return auth.register(request); }
    @PostMapping("/login") Authentication login(@Valid @RequestBody Login request) { return auth.login(request); }
    @GetMapping("/me") Profile me(@RequestAttribute("accountId") String actor) { return auth.profile(actor); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader("Authorization") String header) { auth.logout(header); }
}
