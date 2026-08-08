package com.oms.common.web.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.core.exception.BusinessException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 将下游 Feign 响应体中的 Result{code,message} 转换为 BusinessException。
 */
public class FeignErrorDecoder implements ErrorDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        try (InputStream body = response.body().asInputStream()) {
            String text = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode node = MAPPER.readTree(text);
            int code = node.path("code").asInt(response.status());
            String message = node.path("message").asText("下游服务调用失败");
            return new BusinessException(code, message);
        } catch (Exception ex) {
            return new BusinessException(response.status(), "下游服务调用失败");
        }
    }
}
