package com.kun.onlinejudge.judgeservice.codesandbox;

import com.kun.onlinejudge.model.codesandbox.ExecuteMessage;
import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class ExampleCodeSandBox implements CodeSandBox {
    @Override
    public ExecuteResponse doExecute(ExecuteMessage executeMessage) {
        return null;
    }
}
