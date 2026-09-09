package com.kun.onlinejudge.judgeservice.codesandbox;

import javax.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class CodeSandBoxFactory {

    @Resource
    private ExampleCodeSandBox exampleCodeSandBox;

    @Resource
    private DockerCodeSandBox dockerCodeSandBox;

    public CodeSandBox newInstance(String type) {
        switch (type) {
            case "example":
                return exampleCodeSandBox;
            case "docker":
                return dockerCodeSandBox;
            default:
                return exampleCodeSandBox;
        }
    }
}
