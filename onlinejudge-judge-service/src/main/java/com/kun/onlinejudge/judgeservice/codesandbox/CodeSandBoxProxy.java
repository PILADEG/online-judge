package com.kun.onlinejudge.judgeservice.codesandbox;

import com.kun.onlinejudge.model.codesandbox.ExecuteMessage;
import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeSandBoxProxy implements CodeSandBox{
    private final CodeSandBox codeSandBox;

    public CodeSandBoxProxy(CodeSandBox codeSandBox) {
        this.codeSandBox = codeSandBox;
    }

    @Override
    public ExecuteResponse doExecute(ExecuteMessage executeMessage) {
        log.info("do execute {}", executeMessage);
        ExecuteResponse response = codeSandBox.doExecute(executeMessage);
        log.info("do execute response {}", response);
        return response;
    }
}
