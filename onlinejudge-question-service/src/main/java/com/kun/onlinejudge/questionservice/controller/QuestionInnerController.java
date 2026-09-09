package com.kun.onlinejudge.questionservice.controller;

import com.kun.onlinejudge.exception.BusinessException;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.result.BaseResponse;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.model.result.ResultUtils;
import com.kun.onlinejudge.model.vo.QuestionVO;
import com.kun.onlinejudge.questionservice.service.QuestionService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目服务内网端点：仅供其它服务内网调用，不做用户鉴权；不得经网关对外路由。
 */
@RestController
@RequestMapping("/inner/question")
public class QuestionInnerController {

    @Resource
    private QuestionService questionService;

    @GetMapping("/{id}")
    public BaseResponse<QuestionVO> getQuestionVOById(@PathVariable("id") Long id) {
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(questionService.getQuestionVO(question));
    }
}
