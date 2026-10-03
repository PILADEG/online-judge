package com.kun.onlinejudge.judgeservice.judgement;

public class RetryableJudgeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RetryableJudgeException(String message) {
        super(message);
    }

    public RetryableJudgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
