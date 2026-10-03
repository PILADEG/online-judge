package com.kun.onlinejudge.model.vo;

import cn.hutool.json.JSONUtil;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.judge.JudgeConfig;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

@Data
public class QuestionVO implements Serializable {

    private Long id;

    private String title;

    private String content;

    private Integer submitNum;

    private Integer acceptedNum;

    private JudgeConfig judgeConfig;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    private List<String> tagList;

    private UserVO user;

    public static Question voToObj(QuestionVO questionVO) {
        if (questionVO == null) {
            return null;
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionVO, question);
        List<String> tagList = questionVO.getTagList();
        if (tagList != null) {
            question.setTags(JSONUtil.toJsonStr(tagList));
        }
        JudgeConfig judgeConfig = questionVO.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        return question;
    }

    public static QuestionVO objToVo(Question question) {
        if (question == null) {
            return null;
        }
        QuestionVO questionVO = new QuestionVO();
        BeanUtils.copyProperties(question, questionVO);
        if (StringUtils.isNotBlank(question.getTags())) {
            questionVO.setTagList(JSONUtil.toList(question.getTags(), String.class));
        }
        if (StringUtils.isNotBlank(question.getJudgeConfig())) {
            questionVO.setJudgeConfig(JSONUtil.toBean(question.getJudgeConfig(), JudgeConfig.class));
        }
        return questionVO;
    }
}
