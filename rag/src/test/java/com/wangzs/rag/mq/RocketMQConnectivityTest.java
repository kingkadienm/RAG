package com.wangzs.rag.mq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RocketMQ 连通性测试（纯原生 API，零 Spring 依赖）
 *
 * <p>运行前确保：
 * <pre>
 *   NameServer 已启动：mqnamesrv
 *   Broker  已启动：mqbroker -n 127.0.0.1:9876
 * </pre>
 *
 * <p>运行方式：
 * <pre>mvn test -Dtest=RocketMQConnectivityTest</pre>
 */
public class RocketMQConnectivityTest {

    /** 测试专用 Topic，避免污染生产数据 */
    private static final String TEST_TOPIC = "rag-mq-connectivity-test";
    private static final String NAME_SERVER = "127.0.0.1:9876";

    private DefaultMQProducer producer;
    private DefaultMQPushConsumer consumer;

    // -----------------------------------------------------------------------
    //  初始化 / 销毁
    // -----------------------------------------------------------------------
    @BeforeEach
    public void setUp() throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());

        producer = new DefaultMQProducer("test-producer-" + ts);
        producer.setNamesrvAddr(NAME_SERVER);
        producer.setSendMsgTimeout(10_000);
        producer.setRetryTimesWhenSendFailed(2);
        producer.start();

        consumer = new DefaultMQPushConsumer("test-consumer-" + ts);
        consumer.setNamesrvAddr(NAME_SERVER);
        consumer.subscribe(TEST_TOPIC, "*");
        consumer.start();

        System.out.println("Producer 和 Consumer 已启动, NameServer=" + NAME_SERVER);
    }

    @AfterEach
    public void tearDown() {
        if (producer != null) {
            producer.shutdown();
        }
        if (consumer != null) {
            consumer.shutdown();
        }
        System.out.println("Producer 和 Consumer 已关闭");
    }

    // -----------------------------------------------------------------------
    //  测试 1：仅验证发送（Producer → Broker）
    //  如果这一步超时，说明 NameServer 或 Broker 不可达
    // -----------------------------------------------------------------------
    @Test
    void testSend() throws Exception {
        String content = "mq-send-test-" + System.currentTimeMillis();

        org.apache.rocketmq.common.message.Message msg =
                new org.apache.rocketmq.common.message.Message(
                        TEST_TOPIC, content.getBytes(StandardCharsets.UTF_8));
        msg.setKeys("test-" + System.currentTimeMillis());

        var sendResult = producer.send(msg);

        assertNotNull(sendResult, "发送结果不应为 null");
        assertNotNull(sendResult.getMsgId(), "消息ID不应为 null");
        assertEquals(
                org.apache.rocketmq.client.producer.SendStatus.SEND_OK,
                sendResult.getSendStatus()
        );

        System.out.println("=== 测试 1 通过：发送成功 ===");
        System.out.println("MsgId     : " + sendResult.getMsgId());
        System.out.println("QueueId   : " + sendResult.getMessageQueue().getQueueId());
        System.out.println("BrokerName: " + sendResult.getMessageQueue().getBrokerName());
    }

    // -----------------------------------------------------------------------
    //  测试 2：完整闭环（Producer → Broker → Consumer）
    //  验证消息能被消费者成功接收
    // -----------------------------------------------------------------------
    @Test
    void testSendAndReceive() throws Exception {
        String content = "mq-roundtrip-" + System.currentTimeMillis();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        AtomicReference<Throwable> consumerError = new AtomicReference<>();

        // 注册消费者（每次测试用新 Listener 避免串扰）
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(
                    List<MessageExt> msgs,
                    org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext ctx) {
                try {
                    for (MessageExt msg : msgs) {
                        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                        received.set(body);
                        latch.countDown();
                        System.out.println("消费者收到: body=" + body
                                + ", msgId=" + msg.getMsgId()
                                + ", queueId=" + msg.getQueueId());
                    }
                } catch (Exception e) {
                    consumerError.set(e);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        try {
            // 发送
            org.apache.rocketmq.common.message.Message msg =
                    new org.apache.rocketmq.common.message.Message(
                            TEST_TOPIC, content.getBytes(StandardCharsets.UTF_8));
            msg.setKeys("test-" + System.currentTimeMillis());

            var sendResult = producer.send(msg);
            assertNotNull(sendResult, "发送结果不应为 null");
            System.out.println("消息已发送, msgId=" + sendResult.getMsgId());

            // 等待消费（最多 15 秒）
            boolean receivedInTime = latch.await(15, TimeUnit.SECONDS);

            if (consumerError.get() != null) {
                fail("消费过程出错: " + consumerError.get().getMessage(), consumerError.get());
            }
            assertTrue(receivedInTime,
                    "消息未在 15 秒内被消费，请检查 RocketMQ Broker 是否已启动 "
                            + "(NameServer: " + NAME_SERVER + ", Broker: 127.0.0.1:10911)");

            assertEquals(content, received.get(), "收到的消息内容应与发送内容一致");
            System.out.println("=== 测试 2 通过：发送/接收闭环验证成功 ===");

        } finally {
            // 清理 Listener，避免影响后续测试
            consumer.registerMessageListener(new MessageListenerConcurrently() {
                @Override
                public ConsumeConcurrentlyStatus consumeMessage(
                        List<MessageExt> msgs,
                        org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext ctx) {
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
            });
        }
    }
}
