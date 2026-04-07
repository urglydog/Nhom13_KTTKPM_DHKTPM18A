package iuh.fit.api_gateway.config;

import com.nimbusds.jose.jwk.JWK;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    private static final String[] PUBLIC_URLS = {
            "/auth/**",
            "/eureka/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) throws Exception {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(PUBLIC_URLS).permitAll()
                        .anyExchange().authenticated()
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        ClassPathResource resource = new ClassPathResource("certs/public_key.pem");
        String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        JWK jwk = JWK.parseFromPEMEncodedObjects(pem);
        RSAPublicKey publicKey = jwk.toRSAKey().toRSAPublicKey();

        // Trả về Decoder chuẩn của Spring
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}
