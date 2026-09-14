package com.kun.onlinejudge.judgeservice.judgement;

/**
 * 可重试的判题系统故障（沙箱不可用/超时、DB 抖动、判题执行异常等）。
 *
 * <p>与"业务判定结果"严格区分：编译错误、答案错误、超时、内存超限等属于<b>判定结果</b>，
 * 直接落 {@code FAILED(3)} 并 ack；而本异常表示"根本没判出来"，抛出后由消费端把提交
 * 复位为 {@code WAITING(0)} 并记录原因，交给定时任务重投，重试次数耗尽后置终态
 * {@code RETRY_EXHAUSTED(4)} 等人工处理。
 */
public class RetryableJudgeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RetryableJudgeException(String message) {
        super(message);
    }

    public RetryableJudgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
