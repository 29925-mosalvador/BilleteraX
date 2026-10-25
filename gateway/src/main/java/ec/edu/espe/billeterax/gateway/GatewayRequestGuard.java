package ec.edu.espe.billeterax.gateway;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+20)
public class GatewayRequestGuard extends OncePerRequestFilter {
    private record Window(long minute,int count){}
    private final ConcurrentHashMap<String,Window> attempts=new ConcurrentHashMap<>();
    private final String token;
    GatewayRequestGuard(@Value("${gateway.agent-token}") String token){this.token=token;}
    private void deny(HttpServletResponse response,int status,String code,String message) throws IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\"}");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Referrer-Policy","no-referrer");response.setHeader("X-Frame-Options","DENY");
        String path=request.getRequestURI();
        if(path.startsWith("/api/") || path.startsWith("/agent/"))response.setHeader("Cache-Control","no-store");
        boolean agent=path.equals("/agent/exchange");
        if(agent){
            String given=request.getHeader("X-Billetera-Agent");
            if(given==null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),given.getBytes(StandardCharsets.UTF_8))){deny(response,403,"FORBIDDEN","Acceso no permitido");return;}
        }
        if(path.equals("/api/auth/login") || path.equals("/api/auth/register")){
            long minute=Instant.now().getEpochSecond()/60;
            if(attempts.size()>2000)attempts.entrySet().removeIf(e->e.getValue().minute()!=minute);
            if(attempts.size()>2000){deny(response,429,"TRY_LATER","Reintenta en un minuto");return;}
            var window=attempts.compute(request.getRemoteAddr(),(ip,old)->new Window(minute,old==null||old.minute()!=minute?1:old.count()+1));
            if(window.count()>20){deny(response,429,"TRY_LATER","Demasiados intentos. Reintenta en un minuto");return;}
        }
        if(!request.getMethod().equals("POST")){chain.doFilter(request,response);return;}
        int limit=agent?8*1024*1024:32768;
        byte[] body=request.getInputStream().readNBytes(limit+1);
        if(body.length>limit){deny(response,413,"REQUEST_TOO_LARGE","La solicitud es demasiado grande");return;}
        var input=new ByteArrayInputStream(body);
        var wrapped=new HttpServletRequestWrapper(request){
            @Override public ServletInputStream getInputStream(){return new ServletInputStream(){
                public int read(){return input.read();}
                public int read(byte[] b,int off,int len){return input.read(b,off,len);}
                public boolean isFinished(){return input.available()==0;}
                public boolean isReady(){return true;}
                public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
            };}
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        };
        chain.doFilter(wrapped,response);
    }
}
