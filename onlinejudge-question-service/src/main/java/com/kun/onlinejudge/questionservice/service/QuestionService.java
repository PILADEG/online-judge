package com.kun.onlinejudge.questionservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kun.onlinejudge.model.dto.question.QuestionQueryRequest;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.vo.QuestionVO;

public interface QuestionService extends IService<Question> {

    void validQuestion(Question question, boolean add);

    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);

    QuestionVO getQuestionVO(Question question);

    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage);
}
