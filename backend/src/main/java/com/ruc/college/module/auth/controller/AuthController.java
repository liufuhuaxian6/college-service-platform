package com.ruc.college.module.auth.controller;

import com.ruc.college.common.result.Result;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.auth.service.AuthService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Validated LoginRequest request) {
        Map<String, Object> data = authService.login(request.getStudentId(), request.getPassword());
        return Result.ok(data);
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Validated RegisterRequest request) {
        SysUser user = new SysUser();
        user.setStudentId(request.getStudentId());
        user.setName(request.getName());
        user.setPassword(request.getPassword());
        authService.register(user);
        return Result.ok();
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "学号不能为空")
        private String studentId;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "学号不能为空")
        private String studentId;
        @NotBlank(message = "姓名不能为空")
        private String name;
        @NotBlank(message = "密码不能为空")
        private String password;
    }
}
