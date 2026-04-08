package iuh.fit.auth_service.config;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;

@Configuration
public class JwtConfig {

    @Bean
    public RSAPublicKey loadPublicKey(RSAPrivateKey privateKey) throws Exception {
        if (privateKey instanceof RSAPrivateCrtKey crtKey) {
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                    crtKey.getModulus(),
                    crtKey.getPublicExponent()
            );
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
        }

        var resource = new ClassPathResource("certs/public_key.pem");
        String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return JWK.parseFromPEMEncodedObjects(pem).toRSAKey().toRSAPublicKey();
    }

    @Bean
    public RSAPrivateKey loadPrivateKey() throws Exception {
        String pem = System.getenv("JWT_PRIVATE_KEY");
        if (pem == null || pem.isBlank()) {
            var resource = new ClassPathResource("certs/private_key.pem");
            pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        return JWK.parseFromPEMEncodedObjects(pem).toRSAKey().toRSAPrivateKey();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        // Sửa lỗi cú pháp: RSAKey.Builder là một static inner class
        JWK jwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .build();

        JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwks);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}