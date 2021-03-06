package com.fintech.intellinews.dao;

import com.fintech.intellinews.entity.CommentEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentDao {

    /**
     * 获取指定文章id查询列表
     *
     * @param id 文章id
     * @return 评论列表
     */
    List<CommentEntity> listArticleComments(@Param("articleId") Long id);

    /**
     * 获取用户所有评论
     *
     * @param userId 用户id
     * @return 评论列表
     */
    List<CommentEntity> listUserComments(Long userId);

    /**
     * 添加用户评论
     *
     * @param entity 评论
     * @return 受影响的行数
     */
    Integer addUserComment(CommentEntity entity);

    /**
     * 为指定评论点赞
     *
     * @param commentId 评论id
     */
    Integer updateWithLike(@Param("id") Long commentId);

    /**
     * 为指定评论点踩
     *
     * @param commentId 评论id
     */
    Integer updateWithDislike(@Param("id") Long commentId);
}