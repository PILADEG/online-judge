package com.kun.onlinejudge.userservice.controller;

import com.kun.onlinejudge.exception.BusinessException;
import com.kun.onlinejudge.model.entity.User;
import com.kun.onlinejudge.model.result.BaseResponse;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.model.result.ResultUtils;
import com.kun.onlinejudge.model.vo.UserSnapshotVO;
import com.kun.onlinejudge.model.vo.UserVO;
import com.kun.onlinejudge.userservice.service.UserService;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户服务内部端点：仅供网关/其它服务内网调用，不做用户鉴权；不得经网关对外路由。
 */
@RestController
@RequestMapping("/inner/user")
public class UserInnerController {

    @Resource
    private UserService userService;

    /** 网关每请求取最新认证快照 */
    @GetMapping("/{id}/snapshot")
    public BaseResponse<UserSnapshotVO> snapshot(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        UserSnapshotVO vo = new UserSnapshotVO();
        vo.setId(user.getId());
        vo.setUserRole(user.getUserRole());
        vo.setUserName(user.getUserName());
        vo.setUserAvatar(user.getUserAvatar());
        return ResultUtils.success(vo);
    }

    @GetMapping("/{id}/vo")
    public BaseResponse<UserVO> getUserVOById(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        return ResultUtils.success(userService.getUserVO(user));
    }

    @PostMapping("/list/vo")
    public BaseResponse<List<UserVO>> listUserVOByIds(@RequestBody List<Long> ids) {
        return ResultUtils.success(userService.getUserVO(userService.listByIds(ids)));
    }
}
