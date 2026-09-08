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

/**
 * 题目视图（不含 answer、judgeCases 等敏感字段）
 */
@Data
public class QuestionVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 提交数
     */
    private Integer submitNum;

    /**
     * 通过数
     */
    private Integer acceptedNum;

    /**
     * 判题配置
     */
    private JudgeConfig judgeConfig;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 创建人信息
     */
    private UserVO user;

    /**
     * 包装类转对象
     *
     * @param questionVO
     * @return
     */
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

    /**
     * 对象转包装类
     *
     * @param question
     * @return
     */
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
