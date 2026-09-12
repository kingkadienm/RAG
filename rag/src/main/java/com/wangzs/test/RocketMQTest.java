package com.wangzs.test;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class RocketMQTest {

    /**
     * ============================================================
     * RocketMQ 配置
     * ============================================================
     */

    /**
     * Windows SSH 隧道：
     * <p>
     * localhost:9876
     * ↓
     * Linux RocketMQ NameServer:9876
     */
    private static final String NAMESRV_ADDR = "127.0.0.1:9876";

    /**
     * 你的 Topic
     */
    private static final String TOPIC = "TopicTest";

    /**
     * Producer Group
     */
    private static final String PRODUCER_GROUP = "test-producer-group";

    /**
     * Consumer Group
     */
    private static final String CONSUMER_GROUP = "test-consumer-group";


    public static void main(String[] args) throws Exception {

        System.out.println();
        System.out.println("============================================================");
        System.out.println(" RocketMQ 5.3.1 Test");
        System.out.println("============================================================");
        System.out.println("NameServer : " + NAMESRV_ADDR);
        System.out.println("Topic      : " + TOPIC);
        System.out.println();

        /**
         * 1. 启动 Consumer
         */
        DefaultMQPushConsumer consumer = createConsumer();

        consumer.start();

        System.out.println("Consumer 启动成功");
        System.out.println();

        /**
         * 等 Consumer 注册完成
         */
        Thread.sleep(2000);

        /**
         * 2. 创建 Producer
         */
        DefaultMQProducer producer = createProducer();

        producer.start();

        System.out.println("Producer 启动成功");
        System.out.println();

        /**
         * 3. 发送消息
         */
        sendMessage(producer);

        /**
         * 4. 保持 Consumer 运行
         *
         * 等待消息被消费
         */
        System.out.println();
        System.out.println("Consumer 正在等待消息...");
        System.out.println("按 Ctrl+C 退出");

        Thread.sleep(30_000);

        /**
         * 5. 关闭
         */
        producer.shutdown();
        consumer.shutdown();

        System.out.println();
        System.out.println("RocketMQ Test 完成");
    }


    /**
     * ============================================================
     * 创建 Producer
     * ============================================================
     */
    private static DefaultMQProducer createProducer() {

        DefaultMQProducer producer =
                new DefaultMQProducer(PRODUCER_GROUP);

        /**
         * NameServer
         */
        producer.setNamesrvAddr(NAMESRV_ADDR);

        /**
         * Producer 超时时间
         */
        producer.setSendMsgTimeout(10_000);

        return producer;
    }


    /**
     * ============================================================
     * 创建 Consumer
     * ============================================================
     */
    private static DefaultMQPushConsumer createConsumer()
            throws Exception {

        DefaultMQPushConsumer consumer =
                new DefaultMQPushConsumer(CONSUMER_GROUP);

        /**
         * NameServer
         */
        consumer.setNamesrvAddr(NAMESRV_ADDR);

        /**
         * 从哪里开始消费
         *
         * CONSUME_FROM_FIRST_OFFSET：
         * 从最早的消息开始
         *
         * 这里测试阶段使用它比较方便
         */
        consumer.setConsumeFromWhere(
                ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET
        );

        /**
         * 订阅 Topic
         *
         * "*" = 所有 Tag
         */
        consumer.subscribe(TOPIC, "*");

        /**
         * 注册消息监听器
         */
        consumer.registerMessageListener(
                new MessageListenerConcurrently() {

                    @Override
                    public ConsumeConcurrentlyStatus consumeMessage(
                            List<MessageExt> messages,
                            ConsumeConcurrentlyContext context) {

                        for (MessageExt message : messages) {

                            String body =
                                    new String(
                                            message.getBody(),
                                            StandardCharsets.UTF_8
                                    );

                            System.out.println();
                            System.out.println(
                                    "---------------- 收到消息 ----------------"
                            );

                            System.out.println(
                                    "MessageId   : "
                                            + message.getMsgId()
                            );

                            System.out.println(
                                    "Topic       : "
                                            + message.getTopic()
                            );

                            System.out.println(
                                    "Tags        : "
                                            + message.getTags()
                            );

                            System.out.println(
                                    "QueueId     : "
                                            + message.getQueueId()
                            );

                            System.out.println(
                                    "QueueOffset : "
                                            + message.getQueueOffset()
                            );

                            System.out.println(
                                    "Body        : "
                                            + body
                            );

                            System.out.println(
                                    "-------------------------------------------"
                            );
                        }

                        /**
                         * 告诉 RocketMQ：
                         *
                         * 消息消费成功
                         */
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                }
        );

        return consumer;
    }


    /**
     * ============================================================
     * 发送消息
     * ============================================================
     */
    private static void sendMessage(
            DefaultMQProducer producer) throws Exception {

        String messageBody =
                "Hello RocketMQ 5.3.1 - "
                        + UUID.randomUUID();

        Message message = new Message(
                TOPIC,
                "TEST",
                messageBody.getBytes(StandardCharsets.UTF_8)
        );

        System.out.println(
                "发送消息..."
        );

        System.out.println(
                "Body: " + messageBody
        );

        /**
         * 同步发送
         *
         * send() 会等待 Broker 返回结果
         */
        var result = producer.send(message);

        System.out.println();
        System.out.println(
                "---------------- 发送成功 ----------------"
        );

        System.out.println(
                "MessageId   : "
                        + result.getMsgId()
        );

        System.out.println(
                "SendStatus  : "
                        + result.getSendStatus()
        );

        System.out.println(
                "Queue       : "
                        + result.getMessageQueue()
        );

        System.out.println(
                "QueueOffset : "
                        + result.getQueueOffset()
        );

        System.out.println(
                "-------------------------------------------"
        );
    }
}

