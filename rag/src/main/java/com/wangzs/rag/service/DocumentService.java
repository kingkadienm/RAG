package com.wangzs.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.enums.ParseStatusEnum;
import com.wangzs.rag.enums.VectorStatusEnum;
import com.wangzs.rag.mapper.DocumentMapper;
import com.wangzs.rag.mapper.KnowledgeBaseMapper;
import com.wangzs.rag.model.dto.DocumentParseMsgDTO;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.model.entity.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 文档服务接口
 */
public interface DocumentService extends IService<Document> {

    /**
     * 创建文档记录并触发异步解析消息
     */
    Document create(Long kbId, String title, String fileName, String fileType,
                    Long fileSize, String filePath, String fileMd5, Long creatorId);

    /**
     * 重新解析 / 重试解析文档（自动累加版本号保证幂等）
     */
    void retryParse(Long id);

    /**
     * 统一发送文档解析 MQ 异步消息
     */
    void sendParseMessage(Document doc);

    /**
     * 根据 ID 查询未删除文档详情
     */
    Document getById(Long id);

    /**
     * 分页查询知识库下的文档列表
     */
    Page<Document> pageByKbId(Long kbId, int pageNum, int pageSize);

    /**
     * 查询知识库下的所有未删除文档
     */
    List<Document> listByKbId(Long kbId);

    /**
     * 更新文档解析状态（局部更新）
     */
    void updateParseStatus(Long id, ParseStatusEnum status, Integer chunkCount, String errorMsg);

    /**
     * 更新文档向量化状态（局部更新）
     */
    void updateVectorStatus(Long id, VectorStatusEnum status, Integer vectorCount, String errorMsg);

    /**
     * 更新文档存储路径（局部更新）
     */
    void updateFilePath(Long id, String filePath);

    /**
     * 逻辑删除文档
     */
    void delete(Long id);

    /**
     * 批量删除知识库下的文档
     */
    void deleteByKbId(Long kbId);

    /**
     * 递增文档重试次数
     */
    void incrementRetryCount(Long id);
}