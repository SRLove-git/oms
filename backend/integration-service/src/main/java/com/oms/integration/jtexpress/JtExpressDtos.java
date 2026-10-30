package com.oms.integration.jtexpress;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
            @Size(max = 100) String unit,
            @NotBlank @Size(max = 50) String postcode,
            @JsonProperty("country_code") @Size(min = 2, max = 2) String countryCode,
            @Size(max = 600) String instructions) {

        public ContactDetails(
                String contactName,
                String phoneNumber,
                String email,
                String address,
                String postcode,
                String instructions) {
            this(contactName, phoneNumber, email, address, null, postcode, "SG", instructions);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimeWindow(
            @NotBlank String from,
            @NotBlank String to) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PickupDetails(
            @JsonProperty("contact_name") @NotBlank @Size(max = 100) String contactName,
            @JsonProperty("phone_number") @NotBlank @Size(max = 50) String phoneNumber,
            @Size(max = 100) String email,
            @NotBlank @Size(max = 2000) String address,
            @Size(max = 100) String unit,
            @NotBlank @Size(max = 50) String postcode,
            @JsonProperty("country_code") @Size(min = 2, max = 2) String countryCode,
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date,
            @JsonProperty("time_window") @Valid @NotNull TimeWindow timeWindow) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Money(
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency) {
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
            @JsonProperty("originalTrackingNo") String originalTrackingNo,
            @JsonProperty("service_code") String serviceCode,
            @JsonProperty("merchant_code") String merchantCode,
            @JsonProperty("order_source") String orderSource,
            @JsonProperty("consignee_details") @Valid @NotNull ContactDetails consigneeDetails,
            @JsonProperty("pickup_details") @Valid PickupDetails pickupDetails,
            @JsonProperty("item_details") @Valid @NotEmpty List<ItemDetails> itemDetails,
            @Valid Money cod,
            @JsonProperty("customs_declaration") @Valid Money customsDeclaration) {

        public CreateShipmentRequest(
                String referenceNumber,
                String serviceCode,
                String merchantCode,
                String orderSource,
                ContactDetails consigneeDetails,
                List<ItemDetails> itemDetails) {
            this(
                    referenceNumber,
                    null,
                    serviceCode,
                    merchantCode,
                    orderSource,
                    consigneeDetails,
                    null,
                    itemDetails,
                    null,
                    null);
        }
    }

    public record CreateShipmentResponse(
            @JsonProperty("tracking_id") String trackingId,
            @JsonProperty("reference_number") String referenceNumber,
            @JsonProperty("sorting_code") String sortingCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Activity(
            String name,
            String reason,
            @JsonProperty("created_by") String createdBy,
            @JsonProperty("created_at") String createdAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
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
            String timestamp,
            @JsonProperty("pod_url") String podUrl,
            @JsonProperty("no_of_attempts") Integer noOfAttempts,
            String comments) {
    }

    public record StatusResponse(
            boolean enabled,
            boolean configured,
            String environment,
            String baseUrl,
            boolean webhookConfigured) {
    }

    public record ConnectivityResponse(boolean connected, String serviceCode, int distributionPointCount) {
    }
}
