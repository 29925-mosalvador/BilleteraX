package ec.edu.espe.billeterax;

import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import java.util.List;

@Configuration
public class WebAccessConfig {
    @Bean FilterRegistrationBean<CorsFilter> browserCors(WebOrigins origins) {
        var config=new CorsConfiguration();
        config.setAllowedOrigins(List.of(origins.allowed()));
        config.setAllowedMethods(List.of("GET","POST","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(600L);
        var source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**",config);
        source.registerCorsConfiguration("/ws",config);
        var bean=new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
