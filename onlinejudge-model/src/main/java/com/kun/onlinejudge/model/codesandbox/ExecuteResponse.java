package com.kun.onlinejudge.model.codesandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteResponse implements Serializable {

    private String status;

    private String message;

    private String errorMessage;

    private Long time;

    private Long memory;

    private List<String> outputList;

    private static final long serialVersionUID = 1L;
}
