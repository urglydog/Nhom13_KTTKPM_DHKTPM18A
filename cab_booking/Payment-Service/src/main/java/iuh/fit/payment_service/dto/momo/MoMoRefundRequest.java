package iuh.fit.payment_service.dto.momo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class MoMoRefundRequest {

    @JsonProperty("partnerCode")
    private String partnerCode;

    @JsonProperty("partnerName")
    private String partnerName;

    @JsonProperty("storeId")
    private String storeId;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("transId")
    private Long transId;

    @JsonProperty("amount")
    private long amount;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("lang")
    private String lang;

    @JsonProperty("description")
    private String description;

    @JsonProperty("signature")
    private String signature;
}
