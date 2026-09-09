package com.kun.onlinejudge.questionservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.onlinejudge.constant.CommonConstant;
import com.kun.onlinejudge.exception.BusinessException;
import com.kun.onlinejudge.exception.ThrowUtils;
import com.kun.onlinejudge.model.dto.question.QuestionQueryRequest;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.judge.JudgeCase;
import com.kun.onlinejudge.model.judge.JudgeConfig;
import com.kun.onlinejudge.model.result.BaseResponse;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.model.vo.QuestionVO;
import com.kun.onlinejudge.model.vo.UserVO;
import com.kun.onlinejudge.questionservice.mapper.QuestionMapper;
import com.kun.onlinejudge.questionservice.service.QuestionService;
import com.kun.onlinejudge.serviceclient.UserServiceClient;
import com.kun.onlinejudge.utils.SqlUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 题目服务实现
 */
@Service
@Slf4j
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Resource
    private UserServiceClient userServiceClient;

    @Override
    public void validQuestion(Question question, boolean add) {
        if (question == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String title = question.getTitle();
        String content = question.getContent();
        String answer = question.getAnswer();
        String judgeConfig = question.getJudgeConfig();
        String judgeCases = question.getJudgeCases();
        // 创建时，必填项不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, content,judgeCases,judgeConfig), ErrorCode.PARAMS_ERROR, "标题或内容为空");
        }
        // 有参数则校验
        if (StringUtils.isNotBlank(title) && title.length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (StringUtils.isNotBlank(content) && content.length() > 65535) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容过长");
        }
        if (StringUtils.isNotBlank(answer) && answer.length() > 65535) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "答案过长");
        }
        if (StringUtils.isNotBlank(judgeCases)){
            List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCases, JudgeCase.class);
            ThrowUtils.throwIf(CollUtil.isEmpty(judgeCaseList), ErrorCode.PARAMS_ERROR, "判题用例格式错误");
        }
        // 判题配置校验
        if (StringUtils.isNotBlank(judgeConfig)) {
            JudgeConfig judgeConfigObj = JSONUtil.toBean(judgeConfig, JudgeConfig.class);
            ThrowUtils.throwIf(judgeConfigObj.getTimeLimit() == null || judgeConfigObj.getTimeLimit() <= 0,
                    ErrorCode.PARAMS_ERROR, "时间限制不合法");
            ThrowUtils.throwIf(judgeConfigObj.getMemoryLimit() == null || judgeConfigObj.getMemoryLimit() <= 0,
                    ErrorCode.PARAMS_ERROR, "内存限制不合法");
        }
    }

    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest == null) {
            return queryWrapper;
        }
        String searchText = questionQueryRequest.getSearchText();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        List<String> tagList = questionQueryRequest.getTags();
        Long userId = questionQueryRequest.getUserId();
        // 拼接查询条件
        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
        }
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public QuestionVO getQuestionVO(Question question) {
        QuestionVO questionVO = QuestionVO.objToVo(question);
        Long userId = question.getUserId();
        UserVO user = null;
        if (userId != null && userId > 0) {
            BaseResponse<UserVO> userResp = userServiceClient.getUserVOById(userId);
            if (userResp != null && userResp.getData() != null) {
                user = userResp.getData();
            }
        }
        questionVO.setUser(user);
        return questionVO;
    }

    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(),
                questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionVOPage;
        }
        // 1. 收集作者 id，Feign 批量拉取用户信息
        Set<Long> userIdSet = questionList.stream().map(Question::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userIdUserMap = getUserMap(userIdSet);
        // 2. 填充信息
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            QuestionVO questionVO = QuestionVO.objToVo(question);
            Long userId = question.getUserId();
            questionVO.setUser(userId == null ? null : userIdUserMap.get(userId));
            return questionVO;
        }).collect(Collectors.toList());
        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

    /**
     * 按作者 id 集合经 Feign 批量拉取用户信息，返回 id -> UserVO 映射
     *
     * @param userIdSet
     * @return
     */
    private Map<Long, UserVO> getUserMap(Set<Long> userIdSet) {
        if (CollUtil.isEmpty(userIdSet)) {
            return Collections.emptyMap();
        }
        BaseResponse<List<UserVO>> userResp = userServiceClient.listUserVOByIds(new ArrayList<>(userIdSet));
        if (userResp == null || userResp.getCode() != 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取用户信息失败");
        }
        List<UserVO> userList = userResp.getData();
        if (CollUtil.isEmpty(userList)) {
            return Collections.emptyMap();
        }
        return userList.stream().filter(Objects::nonNull).filter(user -> user.getId() != null)
                .collect(Collectors.toMap(UserVO::getId, user -> user, (u1, u2) -> u1));
    }
}
