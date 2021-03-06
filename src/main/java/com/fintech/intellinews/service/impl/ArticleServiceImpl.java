package com.fintech.intellinews.service.impl;

import com.fintech.intellinews.AppException;
import com.fintech.intellinews.dao.*;
import com.fintech.intellinews.entity.*;
import com.fintech.intellinews.enums.ResultEnum;
import com.fintech.intellinews.service.ArticleService;
import com.fintech.intellinews.service.async.AsyncTaskService;
import com.fintech.intellinews.util.DateUtil;
import com.fintech.intellinews.vo.ArticleVO;
import com.fintech.intellinews.vo.CommentVO;
import com.fintech.intellinews.vo.DetailsArticleVO;
import com.fintech.intellinews.vo.SearchArticleVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author waynechu
 * Created 2017-10-30 17:29
 */
@Service
@SuppressWarnings("unchecked")
public class ArticleServiceImpl implements ArticleService {

    @Autowired
    private ArticleDao articleDao;

    @Autowired
    private ArticleChannelDao articleChannelDao;

    @Autowired
    private ArticleCountDao articleCountDao;

    @Autowired
    private CommentDao commentDao;

    @Autowired
    private UserLoginDao userLoginDao;

    @Autowired
    private AsyncTaskService asyncTaskService;

    /**
     * 根据频道id获取文章
     *
     * @param channelId 频道id
     * @param pageNum   页数
     * @param pageSize  页大小
     * @return 分页后的文章
     */
    @Override
    public PageInfo<ArticleVO> listArticlesByChannelId(Long channelId, int pageNum, int pageSize) {
        if (channelId == 1) {
            return listLatestArticles(pageNum, pageSize);
        }
        PageHelper.startPage(pageNum, pageSize);
        List<ArticleChannelEntity> articleChannelEntityList = articleChannelDao.listByChannelId(channelId);
        if (articleChannelEntityList.isEmpty()) {
            return new PageInfo(articleChannelEntityList);
        }
        List<Long> ids = articleChannelEntityList.stream().map(ArticleChannelEntity::getArticleId).collect(Collectors.toList());

        List<ArticleVO> articleDTOS = new ArrayList<>();
        Map<Long, ArticleEntity> articleEntityMap = articleDao.mapArticlesByIds(ids);
        Map<Long, ArticleCountEntity> articleCountEntityMap = articleCountDao.mapArticleCountByIds(ids);
        for (ArticleChannelEntity articleChannelEntity : articleChannelEntityList) {
            Long articleId = articleChannelEntity.getArticleId();
            ArticleEntity articleEntity = articleEntityMap.get(articleId);
            ArticleCountEntity articleCountEntity = articleCountEntityMap.get(articleId);
            // 初始化文章统计信息
            if (articleCountEntity == null) {
                initArticleCount(articleId);
                articleCountEntity = new ArticleCountEntity();
                articleCountEntity.setViewCount(0);
            }
            String date = DateUtil.toCustomStringFromDate(articleEntity.getGmtCreate());

            ArticleVO articleDTO = new ArticleVO();
            articleDTO.setId(articleEntity.getId());
            articleDTO.setTitle(articleEntity.getTitle());
            articleDTO.setSource(articleEntity.getSource());
            articleDTO.setDate(date);
            articleDTO.setKeywords(articleEntity.getKeywords());
            articleDTO.setViewCount(articleCountEntity.getViewCount());
            articleDTO.setThumbnail(articleEntity.getThumbnail());
            articleDTOS.add(articleDTO);
        }
        PageInfo page = new PageInfo(articleChannelEntityList);
        page.setList(articleDTOS);
        return page;
    }

    /**
     * 初始化文章统计信息
     *
     * @param articleId 文章id
     */
    @Override
    public void initArticleCount(Long articleId) {
        ArticleCountEntity initArticleCountEntity = new ArticleCountEntity();
        initArticleCountEntity.setArticleId(articleId);
        initArticleCountEntity.setLikeCount(0);
        initArticleCountEntity.setDislikeCount(0);
        initArticleCountEntity.setViewCount(0);
        articleCountDao.insertArticleCount(initArticleCountEntity);
    }

    /**
     * 获取最新文章
     *
     * @param pageNum  页数
     * @param pageSize 页大小
     * @return 分页后的最新文章
     */
    private PageInfo<ArticleVO> listLatestArticles(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<ArticleEntity> articleEntityList = articleDao.listLatestArticles();
        if (articleEntityList.isEmpty()) {
            return new PageInfo(articleEntityList);
        }
        List<Long> ids = articleEntityList.stream().map(ArticleEntity::getId).collect(Collectors.toList());

        Map<Long, ArticleCountEntity> articleCountEntityMap = articleCountDao.mapArticleCountByIds(ids);
        List<ArticleVO> articleDTOS = new ArrayList<>();
        for (ArticleEntity articleEntity : articleEntityList) {
            Long articleId = articleEntity.getId();
            ArticleCountEntity articleCountEntity = articleCountEntityMap.get(articleId);
            String date = DateUtil.toCustomStringFromDate(articleEntity.getGmtCreate());

            ArticleVO articleDTO = new ArticleVO();
            articleDTO.setId(articleEntity.getId());
            articleDTO.setTitle(articleEntity.getTitle());
            articleDTO.setSource(articleEntity.getSource());
            articleDTO.setDate(date);
            articleDTO.setKeywords(articleEntity.getKeywords());
            articleDTO.setViewCount(articleCountEntity.getViewCount());
            articleDTO.setThumbnail(articleEntity.getThumbnail());
            articleDTOS.add(articleDTO);
        }
        PageInfo page = new PageInfo(articleEntityList);
        page.setList(articleDTOS);
        return page;
    }

    /**
     * 通过关键字获取文章列表
     *
     * @param keyword  关键字
     * @param pageNum  分页页数
     * @param pageSize 分页条数
     * @return 分页文章列表
     */
    @Override
    public PageInfo<SearchArticleVO> listArticlesByKeyword(String keyword, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<ArticleEntity> searchList = articleDao.listArticleByKeyword(keyword);
        if (searchList.isEmpty()) {
            return new PageInfo(searchList);
        } else {
            // 更新关键字热度
            asyncTaskService.updateKeywordDegree(keyword);
        }
        List<SearchArticleVO> resultList = new ArrayList<>();
        SearchArticleVO articleVO;
        String content;
        Integer showContentSize = 0;
        for (ArticleEntity entity : searchList) {
            articleVO = new SearchArticleVO();
            articleVO.setId(entity.getId());
            articleVO.setTitle(entity.getTitle());
            articleVO.setSource(entity.getSource());
            content = entity.getContent();
            if (content != null) {
                showContentSize = content.length() > 30 ? 30 : content.length();
            }
            articleVO.setContent(entity.getContent().substring(0, showContentSize));
            resultList.add(articleVO);
        }
        PageInfo pageInfo = new PageInfo(searchList);
        pageInfo.setList(resultList);
        return pageInfo;
    }

    /**
     * 根据id获取文章详情
     *
     * @param id 文章id
     * @return 文章详情实体
     */
    @Cacheable(cacheNames = "articleById", key = "#id")
    @Override
    @Transactional(rollbackFor = {RuntimeException.class})
    public DetailsArticleVO getDetailsArticleById(Long id) {
        ArticleEntity articleEntity = articleDao.getArticleById(id);
        if (articleEntity == null) {
            throw new AppException(ResultEnum.ARTICLE_NOT_EXIST_ERROR);
        }
        DetailsArticleVO details = new DetailsArticleVO();
        BeanUtils.copyProperties(articleEntity, details);
        String dateStr = DateUtil.toDetailTimeString(articleEntity.getGmtCreate());
        details.setDate(dateStr);
        // 更新文章浏览量
        asyncTaskService.updateViewCountByArticleId(id);
        return details;
    }

    /**
     * 获取指定文章的评论信息
     *
     * @param id       文章id
     * @param pageNum  分页索引
     * @param pageSize 分页椰页容
     * @return 评论的分页信息
     */
    @Override
    public PageInfo<CommentVO> listArticleComments(Long id, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<CommentEntity> commentEntityList = commentDao.listArticleComments(id);
        if (commentEntityList.isEmpty()) {
            return new PageInfo(commentEntityList);
        }
        List<Long> userIds = commentEntityList.stream().map(CommentEntity::getUserId).collect(Collectors.toList());

        Map<Long, UserLoginEntity> mapUsers = userLoginDao.mapUserLoginByIds(userIds);
        List<CommentVO> resultComments = new ArrayList<>();
        UserLoginEntity userLoginEntity;
        CommentVO commentVO;
        for (CommentEntity entity : commentEntityList) {
            commentVO = new CommentVO();
            BeanUtils.copyProperties(entity, commentVO);
            userLoginEntity = mapUsers.get(entity.getUserId());
            commentVO.setNickName(userLoginEntity.getNickname());
            commentVO.setAvatar(userLoginEntity.getAvatar());
            resultComments.add(commentVO);
        }
        PageInfo pageInfo = new PageInfo(commentEntityList);
        pageInfo.setList(resultComments);
        return pageInfo;
    }
}
