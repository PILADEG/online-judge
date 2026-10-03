package com.kun.onlinejudge.model.dto.file;

import java.io.Serializable;
import lombok.Data;

@Data
public class UploadFileRequest implements Serializable {

    private String biz;

    private static final long serialVersionUID = 1L;
}