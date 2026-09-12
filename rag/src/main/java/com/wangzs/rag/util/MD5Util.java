package com.wangzs.rag.util;

import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 工具类
 */
@Slf4j
public class MD5Util {

    private static final String ALGORITHM = "MD5";

    /**
     * 计算文件的 MD5 值
     */
    public static String calculateFileMD5(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return calculateMD5(fis);
        }
    }

    /**
     * 计算 MultipartFile 的 MD5 值
     */
    public static String calculateFileMD5(MultipartFile file) throws IOException {
        try (var is = file.getInputStream()) {
            return calculateMD5(is);
        }
    }

    /**
     * 计算输入流的 MD5 值
     */
    public static String calculateMD5(InputStream is) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 算法不可用", e);
            throw BizException.of(ErrorCode.SYSTEM_ERROR.getCode(), "MD5 算法不可用");
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
