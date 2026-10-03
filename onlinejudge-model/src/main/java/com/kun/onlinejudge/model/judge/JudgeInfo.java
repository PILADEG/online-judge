package com.kun.onlinejudge.model.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JudgeInfo {

    private String message;
    private Long time;

    private Long memory;
}
