package com.wangzs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangzs.rag.model.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档 Mapper
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /**
     * 查询知识库下的文档列表
     */
    List<Document> selectByKbId(@Param("kbId") Long kbId);

    /**
     * 批量删除知识库下的文档（逻辑删除）
     */
    int deleteByKbId(@Param("kbId") Long kbId);
}
