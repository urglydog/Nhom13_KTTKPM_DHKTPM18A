package iuh.fit.payment_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "momo")
@Getter
@Setter
public class MoMoProperties {

    private String endpoint;
    private String createUrl;
    private String refundUrl;
    private String queryUrl;
    private String confirmUrl;
    private String partnerCode;
    private String accessKey;
    private String secretKey;
    private String notifyUrl;
    private String returnUrl;
    private String lang = "en";
    private String partnerName = "CAB Booking";
    private String storeId = "CAB_BOOKING";
}
