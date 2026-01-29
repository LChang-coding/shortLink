package com.nageoffer.shortlink.project.mq.idempotent;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 消息队列幂等处理器
 * 核心原理：利用 Redis 的 setIfAbsent (SETNX) 原子指令实现分布式锁/标识。
 * 状态流转：不存在(可消费) -> 状态 "0"(正在消费) -> 状态 "1"(消费完成)
 */
@Component
@RequiredArgsConstructor // Lombok注解：自动生成包含 StringRedisTemplate 的构造函数，实现构造器注入
public class MessageQueueIdempotentHandler {

    private final StringRedisTemplate stringRedisTemplate;

    // 幂等标识在 Redis 中的 Key 前缀，方便通过前缀进行批量管理或隔离
    private static final String IDEMPOTENT_KEY_PREFIX = "short-link:idempotent:";

    /**
     * 【步骤1：抢占消费名额】
     * 判断当前消息是否正在被消费或已经消费过
     *
     * @param messageId 消息唯一标识（通常是 RocketMQ 的 MessageID 或业务唯一 Key）
     * @return 如果返回 true，表示该消息“正在消费”或“已消费完”，当前线程应放弃处理；
     * 如果返回 false，表示拿到了消费权，可以开始执行业务。
     */
    public boolean isMessageBeingConsumed(String messageId) {
        String key = IDEMPOTENT_KEY_PREFIX + messageId;
        // setIfAbsent 等同于 Redis 的 SETNX 指令：如果 key 不存在则设置并返回 true，否则返回 false
        // 这里设置默认值 "0"，表示“正在处理中”，并给 2 分钟有效期（防止程序崩溃导致 Key 永不删除）
        return Boolean.FALSE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "0", 2, TimeUnit.MINUTES));
    }

    /**
     * 【步骤2：判断是否彻底完成】
     * 判断消息消费流程是否执行完成（状态是否为 "1"）
     *
     * @param messageId 消息唯一标识
     * @return true 表示该消息之前已经完整处理过了，不需要再跑业务逻辑
     */
    public boolean isAccomplish(String messageId) {
        String key = IDEMPOTENT_KEY_PREFIX + messageId;
        // 从 Redis 获取当前状态，判断是否等于 "1"
        return Objects.equals(stringRedisTemplate.opsForValue().get(key), "1");
    }

    /**
     * 【步骤3：标记为已完成】
     * 当业务代码成功执行完后，调用此方法将标识位改为 "1"
     *
     * @param messageId 消息唯一标识
     */
    public void setAccomplish(String messageId) {
        String key = IDEMPOTENT_KEY_PREFIX + messageId;
        // 将值覆盖为 "1"，并重置 2 分钟过期时间
        // 这里的 2 分钟是为了在短时间内防止重复消息，2 分钟后 key 自动消失释放 Redis 内存
        stringRedisTemplate.opsForValue().set(key, "1", 2, TimeUnit.MINUTES);
    }

    /**
     * 【补偿机制：异常回滚】
     * 如果业务执行过程中抛出了异常，需要删除这个 Key
     * 否则在 2 分钟过期前，这条消息即便重试也会被 isMessageBeingConsumed 拦截
     *
     * @param messageId 消息唯一标识
     */
    public void delMessageProcessed(String messageId) {
        String key = IDEMPOTENT_KEY_PREFIX + messageId;
        stringRedisTemplate.delete(key);
    }
}