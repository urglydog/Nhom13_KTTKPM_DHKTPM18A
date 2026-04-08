package iuh.fit.auth_service.utils;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class JwtUtil {
    JwtEncoder jwtEncoder;

    public String generateToken(Authentication authentication) {
        Instant now = Instant.now();

        // Build các thông tin bên trong Token (Claims)
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self")                     // Người phát hành
                .issuedAt(now)                      // Thời điểm tạo
                .expiresAt(now.plusSeconds(3600))   // Thời điểm hết hạn (1 giờ)
                .subject(authentication.getName())  // Username
                .claim("scope", "USER")        // Quyền hạn (Authorities)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
