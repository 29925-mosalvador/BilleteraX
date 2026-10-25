package ec.edu.espe.billeterax;


import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
@Component
public class AuthFilter extends OncePerRequestFilter {
    private final AccountAuth auth;
    private final SocketTickets tickets;
    AuthFilter(AccountAuth auth,SocketTickets tickets) { this.auth=auth; this.tickets=tickets; }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String path=req.getRequestURI();
        res.setHeader("X-Content-Type-Options","nosniff");
        res.setHeader("Referrer-Policy","no-referrer");
        res.setHeader("X-Frame-Options","DENY");
        if(path.startsWith("/api/") || path.equals("/ws")) res.setHeader("Cache-Control","no-store");
        if(req.getMethod().equals("POST") && (path.equals("/api/auth/register") || path.equals("/api/auth/login"))) {
            chain.doFilter(req,res); return;
        }
        if(path.startsWith("/api/") || path.equals("/ws")) {
            AuthModels.Principal principal=auth.principal(req.getHeader("Authorization"));
            if(principal==null && path.equals("/ws") && req.getMethod().equals("GET"))
                principal=tickets.consume(req.getParameter("ticket"));
            if(principal==null) {
                res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Inicia sesión para continuar\"}"); return;
            }
            req.setAttribute("accountId",principal.accountId());
            req.setAttribute("principal",principal);
        }
        chain.doFilter(req,res);
    }
}
