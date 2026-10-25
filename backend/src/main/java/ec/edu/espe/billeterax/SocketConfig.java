package ec.edu.espe.billeterax;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import java.util.Map;
@Configuration
@EnableWebSocket
public class SocketConfig implements WebSocketConfigurer {
    private final WalletSocket socket;
    private final WebOrigins origins;
    SocketConfig(WalletSocket socket,WebOrigins origins) { this.socket=socket; this.origins=origins; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socket,"/ws").setAllowedOrigins(origins.allowed()).addInterceptors(new HandshakeInterceptor() {
            public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes) {
                AuthModels.Principal principal=request instanceof ServletServerHttpRequest servlet ?
                    (AuthModels.Principal)servlet.getServletRequest().getAttribute("principal") : null;
                if(principal==null) return false;
                attributes.put("accountId",principal.accountId());
                if(principal.sessionHash()!=null) attributes.put("sessionHash",principal.sessionHash());
                if(principal.expiresAt()!=null) attributes.put("expiresAt",principal.expiresAt());
                return true;
            }
            public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception exception) {}
        });
    }
}
