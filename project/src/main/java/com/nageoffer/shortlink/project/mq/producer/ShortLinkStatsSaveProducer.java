/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.shortlink.project.mq.producer;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 短链接监控状态保存消息队列生产者（Producer）。
 *
 * 这个类的职责非常单一：
 * 1) 接收“短链接访问统计/监控”的数据（Map 形式）；
 * 2) 组装成 Spring Messaging 的 {@link Message}；
 * 3) 通过 {@link RocketMQTemplate} 同步发送到 RocketMQ 的某个 Topic。
 *
 * 注意：这里“发送延迟消费短链接统计”的字样仅表示业务语义（统计类消息通常允许延迟处理），
 * 但本方法当前调用的是 syncSend（同步发送），是否延迟要看 Topic/Tag/延迟级别等配置，
 * 以及消费端如何处理。
 */
@Slf4j
// @Component：将该类注册为 Spring Bean（单例默认），Spring 启动时会扫描并创建该对象，方便注入使用。
@Component
// @RequiredArgsConstructor：Lombok 自动生成“包含所有 final 字段 + 标注 @NonNull 字段”的构造函数。
// 这里会生成：ShortLinkStatsSaveProducer(RocketMQTemplate rocketMQTemplate)
// Spring 再利用这个构造函数做依赖注入（构造器注入）。
@RequiredArgsConstructor
public class ShortLinkStatsSaveProducer {

    /**
     * RocketMQTemplate：RocketMQ Spring Boot Starter 提供的模板类。
     * 作用类似于 JdbcTemplate / RedisTemplate：
     * - 封装 RocketMQ Producer 的创建、序列化、发送、异常处理等细节
     * - 提供 syncSend/asyncSend/sendOneWay 等 API
     *
     * 这里用 final + @RequiredArgsConstructor 实现“构造器注入”，优点：
     * - 依赖不可变（更安全）
     * - 更容易写测试（可用构造器传入 mock）
     */
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * statsSaveTopic：要发送到的 Topic 名称。
     *
     * {@code @Value("${rocketmq.producer.topic}")}：从 Spring 环境（application.yaml / application.properties / 环境变量等）读取配置。
     * - 属性 key 为 rocketmq.producer.topic
     * - 读取到的值会在 Bean 初始化时注入到该字段
     *
     * 注意：如果配置缺失，应用启动可能会报错（取决于 Spring 配置是否允许占位符为空）。
     */
    @Value("${rocketmq.producer.topic}")
    private String statsSaveTopic;

    /**
     * 发送（生产）短链接统计消息。
     *
     * @param producerMap 业务数据载体：Map<String,String>。
     *                    - Key/Value 都是 String，方便序列化与跨语言消费。
     *                    - 调用方通常会放入 shortUrl、gid、uv/uvTime、ip、ua 等字段。
     *
     * 该方法做的事情：
     * 1) 生成唯一 keys（用于 RocketMQ message key）；
     * 2) 把 keys 写回 producerMap（让消息体中也能携带，便于排查）；
     * 3) 构建 Spring Message，并设置 RocketMQ 的 keys Header；
     * 4) 同步发送到指定 Topic；
     * 5) 记录日志；捕获一切异常避免影响主流程。
     */
    public void send(Map<String, String> producerMap) {
        // UUID.randomUUID()：生成一个随机 UUID（128 位），用于唯一标识一条消息。
        // toString()：转成形如 "550e8400-e29b-41d4-a716-446655440000" 的字符串。
        String keys = UUID.randomUUID().toString();

        // 将 keys 回填进消息体 Map。
        // 这不是 RocketMQ 的强制要求，但非常有利于：
        // - 你在消费端/日志中直接看到 keys
        // - 根据 keys 追踪一条消息从生产到消费的全链路
        producerMap.put("keys", keys);

        // MessageBuilder：Spring Messaging 用于构建 Message 的构造器。
        // withPayload(producerMap)：设置消息体（payload）为这个 Map。
        // setHeader(k, v)：设置消息头（header）。header 会随消息一起发送。
        // build()：生成不可变的 Message 对象。
        //
        // 这里 setHeader(MessageConst.PROPERTY_KEYS, keys) 的目的：
        // - MessageConst.PROPERTY_KEYS 是 RocketMQ 约定的 key 名（"KEYS"）
        // - RocketMQ 的 message keys 用于：索引、排查、按 keys 查询消息等
        //
        // 注意：这个 header 最终会被 RocketMQTemplate 转换成 RocketMQ 的 MessageExt 属性。
        Message<Map<String, String>> build = MessageBuilder
                .withPayload(producerMap)
                .setHeader(MessageConst.PROPERTY_KEYS, keys)
                .build();

        // SendResult：RocketMQ 客户端返回的发送结果对象。
        // 常用字段包括：sendStatus（发送状态）、msgId（消息唯一 ID）、queue 等。
        SendResult sendResult;

        try {
            // syncSend(destination, message, timeout)：同步发送。
            // - destination：目标地址，通常是 "topic" 或 "topic:tag"。这里直接用 topic。
            // - message：Spring Message，会由 RocketMQTemplate 做转换（包含 payload 序列化 + headers 映射）。
            // - timeout：超时时间（毫秒）。这里 2000L = 2 秒。
            //
            // 同步发送语义：
            // - 当前线程会阻塞等待 Broker 返回 ACK（成功/失败）
            // - 若超时会抛异常
            //
            // 对于“统计类消息”，很多项目也会用 asyncSend 降低延迟；此处选择 syncSend 更稳但会增加接口耗时。
            sendResult = rocketMQTemplate.syncSend(statsSaveTopic, build, 2000L);

            // log.info：Slf4j 打印 INFO 级别日志。
            // {} 是占位符，参数会延迟格式化（性能更好）。
            // sendResult.getSendStatus()：枚举，表示发送是否成功（例如 SEND_OK）。
            // sendResult.getMsgId()：消息 ID（由客户端或 Broker 生成，用于追踪）。
            // keys：我们自己生成并设置的 message keys。
            log.info("[消息访问统计监控] 消息发送结果：{}，消息ID：{}，消息Keys：{}",
                    sendResult.getSendStatus(), sendResult.getMsgId(), keys);
        } catch (Throwable ex) {
            // 这里 catch Throwable 而不是 Exception：意图是“兜住一切异常”，避免统计消息发送失败影响主业务。
            // 代价是：也会吞掉一些严重错误（如 OutOfMemoryError）。不过在“非核心链路”里有些团队会这样做。

            // JSON.toJSONString(producerMap)：将 Map 序列化成 JSON 字符串，便于日志中完整打印消息体。
            // log.error(..., ex)：带异常栈打印。
            log.error("[消息访问统计监控] 消息发送失败，消息体：{}", JSON.toJSONString(producerMap), ex);

            // 自定义行为...
            // 常见做法：
            // 1) 失败后写入本地/Redis/DB 的补偿表，后续定时重试
            // 2) 发送到另一个告警 Topic
            // 3) 打点监控（Prometheus/Micrometer）
            // 4) 降级：直接忽略（对统计链路来说是可接受的）
        }
    }
}
