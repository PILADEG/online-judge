package com.kun.onlinejudge.model.vo;

import cn.hutool.json.JSONUtil;
import com.kun.onlinejudge.model.entity.QuestionSubmit;
import com.kun.onlinejudge.model.judge.JudgeInfo;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

@Data
public class QuestionSubmitVO implements Serializable {

    private Long id;

    private String language;

    private String code;

    private JudgeInfo judgeInfo;

    private Integer status;

    private Long questionId;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    private UserVO user;

    public static QuestionSubmit voToObj(QuestionSubmitVO questionSubmitVO) {
        if (questionSubmitVO == null) {
            return null;
        }
        QuestionSubmit questionSubmit = new QuestionSubmit();
        BeanUtils.copyProperties(questionSubmitVO, questionSubmit);
        JudgeInfo judgeInfo = questionSubmitVO.getJudgeInfo();
        if (judgeInfo != null) {
            questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        }
        return questionSubmit;
    }

    public static QuestionSubmitVO objToVo(QuestionSubmit questionSubmit) {
        if (questionSubmit == null) {
            return null;
        }
        QuestionSubmitVO questionSubmitVO = new QuestionSubmitVO();
        BeanUtils.copyProperties(questionSubmit, questionSubmitVO);
        if (StringUtils.isNotBlank(questionSubmit.getJudgeInfo())) {
            questionSubmitVO.setJudgeInfo(JSONUtil.toBean(questionSubmit.getJudgeInfo(), JudgeInfo.class));
        }
        return questionSubmitVO;
    }
}
