package com.wangzs.rag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wangzs.rag.mapper.UploadRecordMapper;
import com.wangzs.rag.model.entity.UploadRecord;
import com.wangzs.rag.service.UploadRecordService;
import org.springframework.stereotype.Service;

/**
 * 上传记录服务实现类
 */
@Service
public class UploadRecordServiceImpl extends ServiceImpl<UploadRecordMapper, UploadRecord> implements UploadRecordService {
}
