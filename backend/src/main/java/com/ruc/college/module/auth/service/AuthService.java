package com.ruc.college.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.common.security.JwtUtil;
import com.ruc.college.common.util.EncryptUtil;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.auth.mapper.SysUserMapper;
import com.ruc.college.module.system.service.EmailService;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

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

    public Map<String, Object> getProfile() {
        SysUser user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getId());
        profile.put("studentId", user.getStudentId());
        profile.put("name", user.getName());
        profile.put("roleLevel", user.getRoleLevel());
        profile.put("grade", user.getGrade());
        profile.put("major", user.getMajor());
        profile.put("className", user.getClassName());
        profile.put("phone", user.getPhone());
        profile.put("email", emailService.resolveEmail(user));
        profile.put("emailCustom", StringUtils.hasText(user.getEmail()));

        if (user.getIdCardEnc() != null && !user.getIdCardEnc().isBlank()) {
            String decrypted = EncryptUtil.decrypt(user.getIdCardEnc());
            profile.put("idCard", EncryptUtil.desensitize(decrypted, 3, 4));
        }

        return profile;
    }

    /**
     * 用户修改自己可改的资料字段 (当前: 邮箱 / 手机). 不允许改学号/姓名/角色等.
     */
    public void updateMyProfile(String email, String phone) {
        Long uid = UserContext.getUserId();
        SysUser user = userMapper.selectById(uid);
        if (user == null) throw new BusinessException("用户不存在");

        // 使用 UpdateWrapper 显式 SET, 避免 MP 默认忽略 null 字段导致清空操作无法落库
        LambdaUpdateWrapper<SysUser> uw = new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, uid);
        boolean touched = false;

        if (email != null) {
            String e = email.trim();
            if (e.isEmpty()) {
                uw.set(SysUser::getEmail, null);
                touched = true;
            } else {
                if (!e.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    throw new BusinessException("邮箱格式不合法");
                }
                if (e.length() > 100) throw new BusinessException("邮箱过长");
                uw.set(SysUser::getEmail, e);
                touched = true;
            }
        }
        if (phone != null) {
            String p = phone.trim();
            if (p.isEmpty()) {
                uw.set(SysUser::getPhone, null);
                touched = true;
            } else {
                if (!p.matches("^\\d{6,20}$")) throw new BusinessException("手机号格式不合法");
                uw.set(SysUser::getPhone, p);
                touched = true;
            }
        }
        if (touched) userMapper.update(null, uw);
    }
}
