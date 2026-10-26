package com.oms.integration.jtexpress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class JtExpressDtos {

    private JtExpressDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContactDetails(
            @JsonProperty("contact_name") @NotBlank @Size(max = 100) String contactName,
            @JsonProperty("phone_number") @NotBlank @Size(max = 50) String phoneNumber,
            @Size(max = 100) String email,
            @NotBlank @Size(max = 2000) String address,
            @NotBlank @Size(max = 50) String postcode,
            @Size(max = 600) String instructions) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemDetails(
            @DecimalMin("0.01") BigDecimal length,
            @DecimalMin("0.01") BigDecimal width,
            @DecimalMin("0.01") BigDecimal height,
            @NotNull @DecimalMin("0.01") BigDecimal weight,
            @JsonProperty("weight_unit") @NotBlank String weightUnit,
            String description,
            Integer quantity) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateShipmentRequest(
            @JsonProperty("reference_number") @NotBlank @Size(max = 40) String referenceNumber,
            @JsonProperty("service_code") String serviceCode,
            @JsonProperty("merchant_code") String merchantCode,
            @JsonProperty("order_source") String orderSource,
            @JsonProperty("consignee_details") @Valid @NotNull ContactDetails consigneeDetails,
            @JsonProperty("item_details") @Valid @NotEmpty List<ItemDetails> itemDetails) {
    }

    public record CreateShipmentResponse(
            @JsonProperty("tracking_id") String trackingId,
            @JsonProperty("reference_number") String referenceNumber,
            @JsonProperty("sorting_code") String sortingCode) {
    }

    public record Activity(
            String name,
            String reason,
            @JsonProperty("created_by") String createdBy,
            @JsonProperty("created_at") String createdAt) {
    }

    public record TrackingResponse(
            @JsonProperty("tracking_id") String trackingId,
            @JsonProperty("reference_number") String referenceNumber,
            String status,
            @JsonProperty("pod_url") String podUrl,
            @JsonProperty("first_attempt") String firstAttempt,
            List<Activity> activities) {
    }

    public record CancelRequest(@NotBlank String id, String reason) {
    }

    public record CancelResponse(List<String> success, List<Object> errors) {
    }

    public record ConnoteResponse(
            @JsonProperty("reference_number") String referenceNumber,
            @JsonProperty("connote_label_url") String connoteLabelUrl) {
    }

    public record WebhookPayload(
            String status,
            @JsonProperty("tracking_id") String trackingId,
            @JsonProperty("reference_number") String referenceNumber,
            String timestamp) {
    }

    public record StatusResponse(boolean enabled, boolean configured, String environment, String baseUrl) {
    }

    public record ConnectivityResponse(boolean connected, String serviceCode, int distributionPointCount) {
    }
}
