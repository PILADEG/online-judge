package com.kun.onlinejudge.submitservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.onlinejudge.constant.CommonConstant;
import com.kun.onlinejudge.constant.UserConstant;
import com.kun.onlinejudge.exception.BusinessException;
import com.kun.onlinejudge.exception.ThrowUtils;
import com.kun.onlinejudge.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.kun.onlinejudge.model.entity.QuestionSubmit;
import com.kun.onlinejudge.model.enums.QuestionSubmitStatusEnum;
import com.kun.onlinejudge.model.result.BaseResponse;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.model.vo.QuestionSubmitVO;
import com.kun.onlinejudge.model.vo.QuestionVO;
import com.kun.onlinejudge.model.vo.UserVO;
import com.kun.onlinejudge.serviceclient.QuestionServiceClient;
import com.kun.onlinejudge.serviceclient.UserServiceClient;
import com.kun.onlinejudge.submitservice.mapper.QuestionSubmitMapper;
import com.kun.onlinejudge.submitservice.service.QuestionSubmitService;
import com.kun.onlinejudge.utils.SqlUtils;
import com.kun.onlinejudge.utils.UserContext;
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
 * 题目提交服务实现
 */
@Service
@Slf4j
public class QuestionSubmitServiceImpl extends ServiceImpl<QuestionSubmitMapper, QuestionSubmit>
        implements QuestionSubmitService {

    @Resource
    private UserServiceClient userServiceClient;

    @Resource
    private QuestionServiceClient questionServiceClient;

    @Override
    public void validQuestionSubmit(QuestionSubmit questionSubmit, boolean add) {
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String language = questionSubmit.getLanguage();
        String code = questionSubmit.getCode();
        Long questionId = questionSubmit.getQuestionId();
        Integer status = questionSubmit.getStatus();
        // 创建时，必填项不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(language, code), ErrorCode.PARAMS_ERROR, "语言或代码为空");
            ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR, "题目 id 不合法");
            // 题目必须存在
            BaseResponse<QuestionVO> questionResp = questionServiceClient.getQuestionVOById(questionId);
            ThrowUtils.throwIf(questionResp == null || questionResp.getData() == null, ErrorCode.NOT_FOUND_ERROR,
                    "题目不存在");
        }
        // 有参数则校验
        if (StringUtils.isNotBlank(code) && code.length() > 65535) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码过长");
        }
        if (status != null && QuestionSubmitStatusEnum.getEnumByValue(status) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态不合法");
        }
    }

    @Override
    public QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest questionSubmitQueryRequest) {
        QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
        if (questionSubmitQueryRequest == null) {
            return queryWrapper;
        }
        Long id = questionSubmitQueryRequest.getId();
        String language = questionSubmitQueryRequest.getLanguage();
        Integer status = questionSubmitQueryRequest.getStatus();
        Long questionId = questionSubmitQueryRequest.getQuestionId();
        Long userId = questionSubmitQueryRequest.getUserId();
        String sortField = questionSubmitQueryRequest.getSortField();
        String sortOrder = questionSubmitQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(language), "language", language);
        queryWrapper.eq(ObjectUtils.isNotEmpty(status), "status", status);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit questionSubmit) {
        QuestionSubmitVO questionSubmitVO = QuestionSubmitVO.objToVo(questionSubmit);
        Long userId = questionSubmit.getUserId();
        UserVO user = null;
        if (userId != null && userId > 0) {
            BaseResponse<UserVO> userResp = userServiceClient.getUserVOById(userId);
            if (userResp != null && userResp.getData() != null) {
                user = userResp.getData();
            }
        }
        questionSubmitVO.setUser(user);
        // 非本人或管理员，隐藏代码和判题信息
        Long loginUserId = UserContext.getUserId();
        boolean isOwner = loginUserId != null && loginUserId.equals(userId);
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole());
        if (!isOwner && !isAdmin) {
            questionSubmitVO.setCode(null);
            questionSubmitVO.setJudgeInfo(null);
        }
        return questionSubmitVO;
    }

    @Override
    public Page<QuestionSubmitVO> getQuestionSubmitVOPage(Page<QuestionSubmit> questionSubmitPage) {
        List<QuestionSubmit> questionSubmitList = questionSubmitPage.getRecords();
        Page<QuestionSubmitVO> questionSubmitVOPage = new Page<>(questionSubmitPage.getCurrent(),
                questionSubmitPage.getSize(), questionSubmitPage.getTotal());
        if (CollUtil.isEmpty(questionSubmitList)) {
            return questionSubmitVOPage;
        }
        // 1. 关联查询用户信息（Feign 批量拉取）
        Set<Long> userIdSet = questionSubmitList.stream().map(QuestionSubmit::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userIdUserMap = getUserMap(userIdSet);
        // 2. 填充信息
        Long loginUserId = UserContext.getUserId();
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole());
        List<QuestionSubmitVO> questionSubmitVOList = questionSubmitList.stream().map(questionSubmit -> {
            QuestionSubmitVO questionSubmitVO = QuestionSubmitVO.objToVo(questionSubmit);
            Long userId = questionSubmit.getUserId();
            questionSubmitVO.setUser(userId == null ? null : userIdUserMap.get(userId));
            boolean isOwner = loginUserId != null && loginUserId.equals(userId);
            if (!isOwner && !isAdmin) {
                questionSubmitVO.setCode(null);
                questionSubmitVO.setJudgeInfo(null);
            }
            return questionSubmitVO;
        }).collect(Collectors.toList());
        questionSubmitVOPage.setRecords(questionSubmitVOList);
        return questionSubmitVOPage;
    }

    /**
     * 按提交人 id 集合经 Feign 批量拉取用户信息，返回 id -> UserVO 映射
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
