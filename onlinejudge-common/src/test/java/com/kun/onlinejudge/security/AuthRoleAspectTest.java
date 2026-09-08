package com.kun.onlinejudge.security;

import com.kun.onlinejudge.annotation.AuthCheck;
import com.kun.onlinejudge.model.result.BaseResponse;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.model.result.ResultUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AuthRoleAspectTest.TestApp.class)
@AutoConfigureMockMvc
@Import(AuthRoleAspectTest.ProbeController.class)
class AuthRoleAspectTest {

    @SpringBootApplication(scanBasePackages = "com.kun.onlinejudge")
    static class TestApp {
        public static void main(String[] args) {
            SpringApplication.run(TestApp.class, args);
        }
    }

    @RestController
    static class ProbeController {

        @AuthCheck(mustRole = "admin")
        @GetMapping("/probe/admin")
        public BaseResponse<String> admin() {
            return ResultUtils.success("admin-ok");
        }

        @AuthCheck
        @GetMapping("/probe/login")
        public BaseResponse<String> login() {
            return ResultUtils.success("login-ok");
        }

        @GetMapping("/probe/pub")
        public BaseResponse<String> pub() {
            return ResultUtils.success("pub-ok");
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void publicEndpoint_noHeader_ok() throws Exception {
        mvc.perform(get("/probe/pub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void adminEndpoint_noHeader_notLogin() throws Exception {
        mvc.perform(get("/probe/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()));
    }

    @Test
    void adminEndpoint_userRole_forbidden() throws Exception {
        mvc.perform(get("/probe/admin").header("X-User-Role", "user").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
    }

    @Test
    void adminEndpoint_adminRole_ok() throws Exception {
        mvc.perform(get("/probe/admin").header("X-User-Role", "admin").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("admin-ok"));
    }

    @Test
    void loginRequiredEndpoint_adminOrUser_ok() throws Exception {
        mvc.perform(get("/probe/login").header("X-User-Role", "user").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
