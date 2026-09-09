package com.kun.onlinejudge.judgeservice.codesandbox;

import com.kun.onlinejudge.model.codesandbox.ExecuteMessage;
import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;


public interface CodeSandBox {
    public ExecuteResponse doExecute(ExecuteMessage executeMessage);
}
