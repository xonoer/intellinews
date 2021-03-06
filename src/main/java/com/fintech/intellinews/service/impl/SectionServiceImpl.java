package com.fintech.intellinews.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintech.intellinews.AppException;
import com.fintech.intellinews.dao.*;
import com.fintech.intellinews.entity.*;
import com.fintech.intellinews.enums.ResultEnum;
import com.fintech.intellinews.service.ArticleService;
import com.fintech.intellinews.service.SectionService;
import com.fintech.intellinews.service.async.AsyncTaskService;
import com.fintech.intellinews.util.DateUtil;
import com.fintech.intellinews.util.JacksonUtil;
import com.fintech.intellinews.util.StringUtil;
import com.fintech.intellinews.vo.AtlasVO;
import com.fintech.intellinews.vo.DetailsSectionVO;
import com.fintech.intellinews.vo.ListSectionVO;
import com.fintech.intellinews.vo.SearchSectionVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wanghao
 * create 2017-11-08 15:06
 **/
@Service
@SuppressWarnings("unchecked")
public class SectionServiceImpl implements SectionService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SectionDao sectionDao;

    @Autowired
    private SectionCountDao sectionCountDao;

    @Autowired
    private SectionItemDao sectionItemDao;

    @Autowired
    private SectionAliasDao sectionAliasDao;

    @Autowired
    private AtlasDao atlasDao;

    @Autowired
    private ArticleDao articleDao;

    @Autowired
    private ArticleCountDao articleCountDao;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private AsyncTaskService asyncTaskService;

    /**
     * 获取所有条目列表
     *
     * @param pageNum  页数
     * @param pageSize 页大小
     * @return 条目列表
     */
    @Cacheable(cacheNames = "sections", key = "T(String).valueOf(#pageNum).concat('-').concat(#pageSize)")
    @Override
    public PageInfo<ListSectionVO> listSections(Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<SectionEntity> sectionEntities = sectionDao.listAll();
        if (sectionEntities.isEmpty()) {
            return new PageInfo(sectionEntities);
        }
        List<Long> ids = sectionEntities.stream().map(SectionEntity::getId).collect(Collectors.toList());

        Map<Long, SectionCountEntity> sectionCountEntityMap = sectionCountDao.mapSectionCountByIds(ids);
        List<ListSectionVO> resultList = new ArrayList<>();
        ListSectionVO listSectionVO;
        SectionCountEntity sectionCountEntity;
        for (SectionEntity sectionEntity : sectionEntities) {
            Long sectionId = sectionEntity.getId();
            listSectionVO = new ListSectionVO();
            sectionCountEntity = sectionCountEntityMap.get(sectionId);
            if (sectionCountEntity == null) {
                // 初始化条目统计信息
                initSectionCount(sectionId);
                sectionCountEntity = new SectionCountEntity();
                sectionCountEntity.setViewCount(0);
            }
            BeanUtils.copyProperties(sectionEntity, listSectionVO);
            listSectionVO.setViewCount(sectionCountEntity.getViewCount());
            resultList.add(listSectionVO);
        }
        PageInfo pageInfo = new PageInfo(sectionEntities);
        pageInfo.setList(resultList);
        return pageInfo;
    }

    /**
     * 根据关键字查询搜索条目列表
     *
     * @param keyword  关键字
     * @param pageNum  页数
     * @param pageSize 页大小
     * @return 搜索条目列表
     */
    @Override
    public PageInfo<SearchSectionVO> listByKeyword(String keyword, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        // 用 % 分割关键字用于模糊查询
        String signKeyword = StringUtil.spiltString(keyword);
        List<SectionEntity> sectionEntities = sectionDao.listSectionsByKeyword(signKeyword);
        if (sectionEntities.isEmpty()) {
            return new PageInfo(sectionEntities);
        } else {
            // 更新关键字热度
            asyncTaskService.updateKeywordDegree(keyword);
        }
        List<SearchSectionVO> resultList = new ArrayList<>();
        SearchSectionVO searchSectionVO;
        for (SectionEntity sectionEntity : sectionEntities) {
            searchSectionVO = new SearchSectionVO();
            BeanUtils.copyProperties(sectionEntity, searchSectionVO);
            resultList.add(searchSectionVO);
        }
        PageInfo pageInfo = new PageInfo(sectionEntities);
        pageInfo.setList(resultList);
        return pageInfo;
    }

    /**
     * 根据拼音首字母查询导航条目列表（导航查询）
     *
     * @param startWith 拼音首字母
     * @param pageNum   页数
     * @param pageSize  页大小  T(String).valueOf(#pageNum).concat('-').concat(#pageSize)"
     * @return 导航条目列表
     */
    @Cacheable(cacheNames = "sectionsByStartWith", key = "#startWith.concat('-').concat(#pageNum).concat('-').concat(#pageSize)")
    @Override
    public PageInfo<ListSectionVO> listByStartWith(String startWith, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Long> sectionIds = sectionAliasDao.listByStartWith(startWith);
        if (sectionIds.isEmpty()) {
            return new PageInfo(sectionIds);
        }
        Map<Long, SectionEntity> sectionMap = sectionDao.mapSectionByIds(sectionIds);
        Map<Long, SectionCountEntity> sectionCountMap = sectionCountDao.mapSectionCountByIds(sectionIds);
        List<ListSectionVO> listSectionVOS = new ArrayList<>();
        ListSectionVO listSectionVO;
        SectionCountEntity sectionCountEntity;
        for (SectionEntity sectionEntity : sectionMap.values()) {
            listSectionVO = new ListSectionVO();
            sectionCountEntity = sectionCountMap.get(sectionEntity.getId());
            BeanUtils.copyProperties(sectionEntity, listSectionVO);
            BeanUtils.copyProperties(sectionCountEntity, listSectionVO);
            listSectionVOS.add(listSectionVO);
        }
        PageInfo pageInfo = new PageInfo(sectionIds);
        pageInfo.setList(listSectionVOS);
        return pageInfo;
    }

    /**
     * 根据id查询条目详情
     *
     * @param sectionId 条目id
     * @return 条目详情
     */
    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public DetailsSectionVO getSectionById(Long sectionId) {
        SectionEntity sectionEntity = sectionDao.getSectionById(sectionId);
        if (sectionEntity == null) {
            throw new AppException(ResultEnum.SECTION_NOT_EXIST_ERROR);
        }
        SectionItemEntity sectionItemEntity = sectionItemDao.getBySectionId(sectionId);
        SectionCountEntity sectionCountEntity = sectionCountDao.getBySectionId(sectionId);
        if (sectionCountEntity == null) {
            // 初始化条目统计信息
            initSectionCount(sectionId);
            sectionCountEntity = new SectionCountEntity();
            sectionCountEntity.setViewCount(0);
            sectionCountEntity.setShareCount(0);
            sectionCountEntity.setCollectCount(0);
        }
        ObjectNode itemInfo = JacksonUtil.toObjectNodeFromString(objectMapper, sectionItemEntity.getItemInfo());
        // 创建时间为section主表的创建时间
        String createTime = DateUtil.toCustomStringFromDate(sectionEntity.getGmtCreate());
        // 修改时间为section主表和item扩展信息表中选择最新修改的为修改时间
        Date date = sectionEntity.getGmtModified().after(sectionItemEntity.getGmtModified()) ? sectionEntity
                .getGmtModified() : sectionItemEntity.getGmtModified();
        String updateTime = DateUtil.toCustomStringFromDate(date);

        DetailsSectionVO detailsSectionVO = new DetailsSectionVO();
        BeanUtils.copyProperties(sectionEntity, detailsSectionVO);
        detailsSectionVO.setViewCount(sectionCountEntity.getViewCount());
        detailsSectionVO.setShareCount(sectionCountEntity.getShareCount());
        detailsSectionVO.setCollectCount(sectionCountEntity.getCollectCount());
        detailsSectionVO.setItemInfo(itemInfo);
        detailsSectionVO.setCreateTime(createTime);
        detailsSectionVO.setUpdateTime(updateTime);
        // 更新条目浏览量
        sectionCountDao.updateViewCountBySectionId(sectionId);
        return detailsSectionVO;
    }

    /**
     * 根据条目id查询图谱信息
     *
     * @param sectionId 条目id
     * @return 图谱信息
     */
    @Override
    public Map<String, Object> listBySectionIdAndAtlasType(Long sectionId, String atlasType) {
        Map<String, Object> result = new HashMap(2);
        // 添加中心点信息
        SectionEntity sectionEntity = sectionDao.getSectionById(sectionId);
        Map<String, Object> centerInfo = new HashMap<>(2);
        centerInfo.put("id", sectionEntity.getId());
        centerInfo.put("logo", sectionEntity.getLogo());
        centerInfo.put("title", sectionEntity.getName());
        List<AtlasVO> atlasVOS;
        if ("section".equals(atlasType)) {
            // 添加5条相关条目信息
            atlasVOS = listSectionsBySectionId(sectionId);
        } else if ("article".equals(atlasType)) {
            // 添加5条相关文章信息
            atlasVOS = listArticlesBySectionId(sectionId);
        } else {
            throw new AppException(ResultEnum.ATLAS_TYPE_NOT_EXIST_ERROR);
        }
        result.put("center", centerInfo);
        result.put("atlas", atlasVOS);
        return result;
    }

    /**
     * 根据条目id获取相关条目列表
     *
     * @param sectionId 条目id
     * @return 相关条目列表
     */
    private List<AtlasVO> listSectionsBySectionId(Long sectionId) {
        List<AtlasEntity> atlasSectionEntities = atlasDao.listBySectionIdAndType(sectionId, "section", 5);
        List<Long> ids = atlasSectionEntities.stream().map(AtlasEntity::getRelationId).collect(Collectors.toList());

        Map<Long, SectionEntity> sectionMap = new HashMap<>();
        Map<Long, SectionCountEntity> sectionCountMap = new HashMap<>();
        if (!ids.isEmpty()) {
            sectionMap = sectionDao.mapSectionByIds(ids);
            sectionCountMap = sectionCountDao.mapSectionCountByIds(ids);
        }
        List<AtlasVO> atlasSectionVOS = new ArrayList<>();
        Integer maxSectionViewCount = sectionCountDao.getMaxViewCount();
        for (AtlasEntity atlasSectionEntity : atlasSectionEntities) {
            Long relationId = atlasSectionEntity.getRelationId();

            AtlasVO atlasSectionVO = new AtlasVO();
            atlasSectionVO.setId(relationId);
            atlasSectionVO.setTitle(sectionMap.get(relationId).getName());
            atlasSectionVO.setDistance(atlasSectionEntity.getRelationDegree());
            atlasSectionVO.setLogo(sectionMap.get(relationId).getLogo());
            SectionCountEntity sectionCountEntity = sectionCountMap.get(relationId);
            if (sectionCountEntity == null) {
                // 初始化条目统计信息
                initSectionCount(relationId);
                sectionCountEntity = new SectionCountEntity();
                sectionCountEntity.setViewCount(0);
            }
            Integer viewCount = sectionCountEntity.getViewCount();
            Integer weight = convertToWeight(viewCount, maxSectionViewCount);
            atlasSectionVO.setWeight(weight);
            atlasSectionVOS.add(atlasSectionVO);
        }
        return atlasSectionVOS;
    }

    /**
     * 根据条目id获取相关文章列表
     *
     * @param sectionId 条目id
     * @return 相关文章列表
     */
    private List<AtlasVO> listArticlesBySectionId(Long sectionId) {
        List<AtlasEntity> atlasArticleEntities = atlasDao.listBySectionIdAndType(sectionId, "article", 5);
        List<Long> articleIds = atlasArticleEntities.stream().map(AtlasEntity::getRelationId).collect(Collectors.toList());

        Map<Long, ArticleEntity> articleMap = new HashMap<>();
        Map<Long, ArticleCountEntity> articleCountMap = new HashMap<>();
        if (!articleIds.isEmpty()) {
            articleMap = articleDao.mapArticlesByIds(articleIds);
            articleCountMap = articleCountDao.mapArticleCountByIds(articleIds);
        }
        List<AtlasVO> atlasArticleVOS = new ArrayList<>();
        Integer maxArticleViewCount = articleCountDao.getMaxViewCount();
        for (AtlasEntity atlasArticleEntity : atlasArticleEntities) {
            Long relationId = atlasArticleEntity.getRelationId();

            AtlasVO atlasArticleVO = new AtlasVO();
            atlasArticleVO.setId(relationId);
            atlasArticleVO.setTitle(articleMap.get(relationId).getTitle());
            atlasArticleVO.setDistance(atlasArticleEntity.getRelationDegree());
            ArticleCountEntity articleCountEntity = articleCountMap.get(relationId);
            if (articleCountEntity == null) {
                // 初始化文章统计信息
                articleService.initArticleCount(relationId);
                articleCountEntity = new ArticleCountEntity();
                articleCountEntity.setViewCount(0);
            }
            Integer viewCount = articleCountEntity.getViewCount();
            Integer weight = convertToWeight(viewCount, maxArticleViewCount);
            atlasArticleVO.setWeight(weight);
            atlasArticleVOS.add(atlasArticleVO);
        }
        return atlasArticleVOS;
    }

    /**
     * 初始化条目统计信息
     *
     * @param sectionId 条目id
     */
    private void initSectionCount(Long sectionId) {
        SectionCountEntity sectionCountEntity = new SectionCountEntity();
        sectionCountEntity.setSectionId(sectionId);
        sectionCountEntity.setViewCount(0);
        sectionCountEntity.setShareCount(0);
        sectionCountEntity.setCollectCount(0);
        sectionCountDao.initSectionCount(sectionCountEntity);
    }

    /**
     * 浏览量转化为权重
     *
     * @param viewCount 浏览量
     * @return 权重
     */
    private Integer convertToWeight(Integer viewCount, Integer maxViewCount) {
        if (viewCount == 0) {
            return 1;
        }
        return viewCount * 50 / maxViewCount;
    }

}
