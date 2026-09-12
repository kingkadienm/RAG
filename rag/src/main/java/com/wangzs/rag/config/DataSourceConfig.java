package com.wangzs.rag.config;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 数据源配置
 * <p>
 * MySQL：
 * MySQL DataSource
 * ↓
 * MyBatis-Plus SqlSessionFactory
 * ↓
 * Mapper
 * <p>
 * PostgreSQL：
 * PostgreSQL DataSource
 * ↓
 * JdbcTemplate
 * ↓
 * PgVectorStore
 */
@Configuration
public class DataSourceConfig {

    // ============================================================
    // MyBatis-Plus
    // ============================================================

    /**
     * MyBatis-Plus 插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    // ============================================================
    // MySQL
    // ============================================================

    /**
     * MySQL 数据源
     */
    @Primary
    @Bean(name = "mysqlDataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder
                .create()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * MySQL SqlSessionFactory
     */
    @Primary
    @Bean(name = "mysqlSqlSessionFactory")
    public SqlSessionFactory mysqlSqlSessionFactory(
            @Qualifier("mysqlDataSource") DataSource dataSource,
            MybatisPlusInterceptor interceptor,
            MyMetaObjectHandler metaObjectHandler
    ) throws Exception {

        MybatisSqlSessionFactoryBean factoryBean =
                new MybatisSqlSessionFactoryBean();

        // DataSource
        factoryBean.setDataSource(dataSource);

        // Mapper XML
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        Resource[] resources =
                resolver.getResources(
                        "classpath*:/mapper/**/*.xml"
                );
        factoryBean.setMapperLocations(resources);
        // MyBatis-Plus 插件
        factoryBean.setPlugins(interceptor);
        // MyBatis-Plus 全局配置
        GlobalConfig globalConfig = new GlobalConfig()
                .setBanner(false)
                .setMetaObjectHandler(metaObjectHandler);
        factoryBean.setGlobalConfig(globalConfig);
        return factoryBean.getObject();
    }

    /**
     * MySQL SqlSessionTemplate
     */
    @Primary
    @Bean(name = "mysqlSqlSessionTemplate")
    public SqlSessionTemplate mysqlSqlSessionTemplate(@Qualifier("mysqlSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    // ============================================================
    // PostgreSQL + pgvector
    // ============================================================

    /**
     * PostgreSQL 数据源
     */
    @Bean(name = "pgVectorDataSource")
    @ConfigurationProperties(prefix = "app.datasource.pgvector")
    public DataSource pgVectorDataSource() {
        return DataSourceBuilder
                .create()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * PostgreSQL JdbcTemplate
     */
    @Bean(name = "pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Spring AI PgVectorStore
     */
    @Bean
    public VectorStore vectorStore(
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore
                .builder(jdbcTemplate, embeddingModel)
                .build();
    }
}