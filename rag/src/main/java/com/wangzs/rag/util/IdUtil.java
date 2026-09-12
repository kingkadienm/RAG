package com.wangzs.rag.util;

import java.net.InetAddress;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ID 生成工具
 */
public class IdUtil {

    private static final int TIMESTAMP_BITS = 41;
    private static final int MACHINE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MACHINE_ID = resolveMachineId();

    private static long lastTimestamp = -1L;
    private static long sequence = 0L;

    private IdUtil() {
    }

    /**
     * 生成类似 Snowflake 的分布式 ID
     */
    public static long generateId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(timestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;

        return (timestamp << (MACHINE_ID_BITS + SEQUENCE_BITS))
                | (MACHINE_ID << SEQUENCE_BITS)
                | sequence;
    }

    /**
     * 生成短 traceId（时间戳 + 随机数，16 进制字符串）
     */
    public static String generateTraceId() {
        long timestamp = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(0x10000); // 4 位十六进制
        return Long.toHexString(timestamp) + String.format("%04x", random);
    }

    private static long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    private static long resolveMachineId() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            byte[] bytes = addr.getHostAddress().getBytes();
            return ((long) (bytes[bytes.length - 1] & 0xFF) << 2) & ((1L << MACHINE_ID_BITS) - 1);
        } catch (Exception e) {
            return ThreadLocalRandom.current().nextLong(0, 1 << MACHINE_ID_BITS);
        }
    }
}
