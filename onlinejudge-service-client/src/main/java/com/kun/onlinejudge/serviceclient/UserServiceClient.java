package com.kun.onlinejudge.serviceclient;

import com.kun.onlinejudge.model.result.BaseResponse;
import com.kun.onlinejudge.model.vo.UserVO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用户服务内部契约（消费方视角；由 user-service 的 /api/inner/user/** 提供）
 */
@FeignClient(name = "onlinejudge-user-service", contextId = "userServiceClient")
public interface UserServiceClient {

    @GetMapping("/api/inner/user/{id}/vo")
    BaseResponse<UserVO> getUserVOById(@PathVariable("id") Long id);

    @PostMapping("/api/inner/user/list/vo")
    BaseResponse<List<UserVO>> listUserVOByIds(@RequestBody List<Long> ids);
}
