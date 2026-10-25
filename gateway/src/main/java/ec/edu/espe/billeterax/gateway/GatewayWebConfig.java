package ec.edu.espe.billeterax.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.time.Instant;
import java.util.*;

@Configuration
@EnableWebSocket
public class GatewayWebConfig implements WebSocketConfigurer {
    private final MessageBroker broker;
    private final SocketHub sockets;
    private final ObjectMapper mapper;
    private final String[] origins;
    GatewayWebConfig(MessageBroker broker,SocketHub sockets,ObjectMapper mapper,@Value("${gateway.web-origins}") String origins){
        this.broker=broker;this.sockets=sockets;this.mapper=mapper;
        this.origins=Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toArray(String[]::new);
        for(String origin:this.origins){var uri=java.net.URI.create(origin);if(uri.getHost()==null || !Set.of("http","https").contains(uri.getScheme()) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !uri.getPath().isEmpty())throw new IllegalArgumentException("Configura orígenes web exactos");}
    }
    @Bean FilterRegistrationBean<CorsFilter> cors(){
        var c=new CorsConfiguration();c.setAllowedOrigins(List.of(origins));c.setAllowedMethods(List.of("GET","POST","OPTIONS"));c.setAllowedHeaders(List.of("Authorization","Content-Type"));c.setAllowCredentials(false);c.setMaxAge(600L);
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/**",c);source.registerCorsConfiguration("/ws",c);
        var bean=new FilterRegistrationBean<>(new CorsFilter(source));bean.setOrder(Ordered.HIGHEST_PRECEDENCE+10);return bean;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
        registry.addHandler(sockets,"/ws").setAllowedOrigins(origins).addInterceptors(new HandshakeInterceptor(){
            public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes){
                try{
                    String auth=request.getHeaders().getFirst("Authorization");
                    String ticket=request instanceof ServletServerHttpRequest servlet?servlet.getServletRequest().getParameter("ticket"):null;
                    if(auth==null && ticket==null){response.setStatusCode(HttpStatus.UNAUTHORIZED);return false;}
                    var result=broker.identity(auth!=null?"AUTH_SOCKET":"CONSUME_TICKET",mapper.valueToTree(Map.of("ticket",ticket==null?"":ticket)),auth);
                    if(result.status()!=200){response.setStatusCode(HttpStatus.valueOf(result.status()));return false;}
                    var identity=result.body();attributes.put("accountId",identity.path("accountId").asText());
                    if(identity.hasNonNull("sessionHash"))attributes.put("sessionHash",identity.path("sessionHash").asText());
                    if(identity.hasNonNull("expiresAt"))attributes.put("expiresAt",Instant.parse(identity.path("expiresAt").asText()));
                    return true;
                }catch(Exception e){response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);return false;}
            }
            public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception e){}
        });
    }
}
