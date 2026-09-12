package com.wangzs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangzs.rag.model.entity.UploadRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件上传记录 Mapper
 */
@Mapper
public interface UploadRecordMapper extends BaseMapper<UploadRecord> {

}
