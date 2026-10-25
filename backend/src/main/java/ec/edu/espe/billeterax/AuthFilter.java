package ec.edu.espe.billeterax;


import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
@Component
public class AuthFilter extends OncePerRequestFilter {
    private final AccountAuth auth;
    AuthFilter(AccountAuth auth) { this.auth=auth; }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String path=req.getRequestURI();
        if(req.getMethod().equals("POST") && (path.equals("/api/auth/register") || path.equals("/api/auth/login"))) {
            chain.doFilter(req,res); return;
        }
        if(path.startsWith("/api/") || path.equals("/ws")) {
            String account=auth.authenticate(req.getHeader("Authorization"));
            if(account==null) {
                res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Inicia sesión para continuar\"}"); return;
            }
            req.setAttribute("accountId",account);
        }
        chain.doFilter(req,res);
    }
}
