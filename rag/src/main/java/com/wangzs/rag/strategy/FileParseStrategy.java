package com.wangzs.rag.strategy;

import java.io.InputStream;

/**
 * 文件解析策略接口
 */
public interface FileParseStrategy {

    /**
     * 判断是否支持该文件类型
     */
    boolean supports(String fileType);

    /**
     * 解析文件内容
     *
     * @param inputStream 文件字节
     * @param fileName  原始文件名
     * @param mimeType  MIME 类型
     * @return 提取的纯文本内容
     */
//    String parse(byte[] fileBytes, String fileName, String mimeType) throws Exception;
    String parse(InputStream inputStream, String fileName, String mimeType) throws Exception;
}
