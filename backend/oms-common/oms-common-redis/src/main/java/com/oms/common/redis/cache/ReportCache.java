package com.oms.common.redis.cache;

import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 报表查询结果缓存：key 前缀统一为 {@code oms:report:}，value 使用 RedisTemplate 的 JSON 序列化。
 */
@Component
public class ReportCache {

    private final RedisTemplate<String, Object> redisTemplate;

    public ReportCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return (T) value;
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public String key(String... parts) {
        return "oms:report:" + String.join(":", parts);
    }
}
