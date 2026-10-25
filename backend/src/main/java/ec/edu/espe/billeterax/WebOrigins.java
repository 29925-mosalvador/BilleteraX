package ec.edu.espe.billeterax;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
public class WebOrigins {
    private final String[] allowed;
    WebOrigins(@Value("${wallet.web-origins:https://29925-mosalvador.github.io}") String origins) {
        allowed=Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        for(String origin:allowed) {
            java.net.URI uri=java.net.URI.create(origin);
            if(!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null ||
                uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null ||
                (uri.getPath()!=null && !uri.getPath().isEmpty()))
                throw new IllegalArgumentException("WALLET_WEB_ORIGINS requiere orígenes exactos separados por coma, sin rutas ni comodines");
        }
    }
    String[] allowed() { return allowed.clone(); }
}
