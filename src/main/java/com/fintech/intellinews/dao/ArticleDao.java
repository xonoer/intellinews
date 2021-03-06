package com.fintech.intellinews.dao;

import com.fintech.intellinews.entity.ArticleEntity;
import org.apache.ibatis.annotations.MapKey;

import java.util.List;
import java.util.Map;

public interface ArticleDao {

    /**
     * 根据关键搜索文章
     *
     * @param keyword 关键字
     * @return 文章列表
     */
    List<ArticleEntity> listArticleByKeyword(String keyword);

    /**
     * 获取最新文章列表
     *
     * @return 文章列表
     */
    List<ArticleEntity> listLatestArticles();

    /**
     * 批量查询文章
     *
     * @param list id集合
     * @return 文章列表
     */
    List<ArticleEntity> listArticlesByIds(List<Long> list);

    /**
     * 批量查询文章
     *
     * @param list id集合
     * @return 文章列表
     */
    @MapKey("id")
    Map<Long, ArticleEntity> mapArticlesByIds(List<Long> list);

    /**
     * 根据文章id获取文章详情
     *
     * @param id 文章id
     * @return 文章详情
     */
    ArticleEntity getArticleById(Long id);
}