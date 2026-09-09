package com.kun.onlinejudge.submitservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kun.onlinejudge.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.kun.onlinejudge.model.entity.QuestionSubmit;
import com.kun.onlinejudge.model.vo.QuestionSubmitVO;

/**
 * 题目提交服务
 */
public interface QuestionSubmitService extends IService<QuestionSubmit> {

    /**
     * 校验
     *
     * @param questionSubmit
     * @param add            是否为创建校验
     */
    void validQuestionSubmit(QuestionSubmit questionSubmit, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionSubmitQueryRequest
     * @return
     */
    QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest questionSubmitQueryRequest);

    /**
     * 获取题目提交封装（非本人或管理员时隐藏 code、judgeInfo）
     *
     * @param questionSubmit
     * @return
     */
    QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit questionSubmit);

    /**
     * 分页获取题目提交封装（非本人或管理员时隐藏 code、judgeInfo）
     *
     * @param questionSubmitPage
     * @return
     */
    Page<QuestionSubmitVO> getQuestionSubmitVOPage(Page<QuestionSubmit> questionSubmitPage);
}
