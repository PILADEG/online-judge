package com.kun.onlinejudge.serviceclient;

import com.kun.onlinejudge.model.result.BaseResponse;
import com.kun.onlinejudge.model.vo.QuestionVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "onlinejudge-question-service", contextId = "questionServiceClient")
public interface QuestionServiceClient {

    @GetMapping("/api/inner/question/{id}")
    BaseResponse<QuestionVO> getQuestionVOById(@PathVariable("id") Long id);
}
