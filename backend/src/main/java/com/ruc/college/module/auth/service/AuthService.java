package com.ruc.college.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.JwtUtil;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.auth.mapper.SysUserMapper;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;

    public Map<String, Object> login(String studentId, String password) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStudentId, studentId)
        );
        if (user == null) {
            throw new BusinessException("学号不存在");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getStudentId(), user.getRoleLevel());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("name", user.getName());
        result.put("roleLevel", user.getRoleLevel());
        result.put("studentId", user.getStudentId());
        return result;
    }

    public void register(SysUser user) {
        SysUser existing = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStudentId, user.getStudentId())
        );
        if (existing != null) {
            throw new BusinessException("该学号已注册");
        }
        user.setPassword(BCrypt.hashpw(user.getPassword()));
        user.setStatus(1);
        user.setRoleLevel(4); // 默认普通学生
        userMapper.insert(user);
    }
}
