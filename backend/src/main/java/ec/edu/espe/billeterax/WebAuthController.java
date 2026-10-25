package ec.edu.espe.billeterax;

import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
public class WebAuthController {
    private final AccountAuth auth;
    private final SocketTickets tickets;
    WebAuthController(AccountAuth auth,SocketTickets tickets) { this.auth=auth; this.tickets=tickets; }
    @PostMapping("/ws-ticket")
    SocketTickets.Ticket ticket(@RequestHeader("Authorization") String header) {
        return tickets.issue(auth.principal(header));
    }
}
