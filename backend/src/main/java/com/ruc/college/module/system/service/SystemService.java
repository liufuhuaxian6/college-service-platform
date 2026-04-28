package com.ruc.college.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.exception.BusinessException;
import com.ruc.college.common.security.UserContext;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.auth.mapper.SysUserMapper;
import com.ruc.college.module.system.entity.SysNotification;
import com.ruc.college.module.system.entity.SysOperationLog;
import com.ruc.college.module.system.mapper.SysNotificationMapper;
import com.ruc.college.module.system.mapper.SysOperationLogMapper;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemService {

    private final SysUserMapper userMapper;
    private final SysOperationLogMapper logMapper;
    private final SysNotificationMapper notificationMapper;

    // ==================== 用户管理 ====================

    public Page<SysUser> getUserPage(int page, int size, String grade, String major) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(grade != null, SysUser::getGrade, grade)
                .eq(major != null, SysUser::getMajor, major)
                .orderByAsc(SysUser::getStudentId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }

    public SysUser getUserDetail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setPassword(null);
        return user;
    }

    public void updateUser(Long id, SysUser user) {
        SysUser existing = userMapper.selectById(id);
        if (existing == null) throw new BusinessException("用户不存在");
        user.setId(id);
        user.setPassword(null); // 不允许通过此接口改密码
        userMapper.updateById(user);
    }

    public void setUserRole(Long id, int roleLevel) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setRoleLevel(roleLevel);
        userMapper.updateById(user);
    }

    public void changePassword(String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(UserContext.getUserId());
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(BCrypt.hashpw(newPassword));
        userMapper.updateById(user);
    }

    // ==================== 数据概览 ====================

    public Map<String, Object> getDashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalStudents", userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleLevel, 4).eq(SysUser::getStatus, 1)));
        data.put("totalUsers", userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1)));
        return data;
    }

    // ==================== 操作日志 ====================

    public Page<SysOperationLog> getLogPage(int page, int size, String module, String startDate, String endDate) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .eq(module != null, SysOperationLog::getModule, module)
                .ge(startDate != null, SysOperationLog::getCreatedAt, startDate)
                .le(endDate != null, SysOperationLog::getCreatedAt, endDate)
                .orderByDesc(SysOperationLog::getCreatedAt);
        return logMapper.selectPage(new Page<>(page, size), wrapper);
    }

    // ==================== 通知消息 ====================

    public Page<SysNotification> getNotifications(int page, int size, String type) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, UserContext.getUserId())
                .eq(type != null, SysNotification::getType, type)
                .orderByDesc(SysNotification::getCreatedAt);
        return notificationMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public long getUnreadCount() {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getUserId, UserContext.getUserId())
                        .eq(SysNotification::getIsRead, false)
        );
    }

    public void markRead(Long id) {
        notificationMapper.update(null,
                new LambdaUpdateWrapper<SysNotification>()
                        .eq(SysNotification::getId, id)
                        .eq(SysNotification::getUserId, UserContext.getUserId())
                        .set(SysNotification::getIsRead, true)
        );
    }

    public void markAllRead() {
        notificationMapper.update(null,
                new LambdaUpdateWrapper<SysNotification>()
                        .eq(SysNotification::getUserId, UserContext.getUserId())
                        .eq(SysNotification::getIsRead, false)
                        .set(SysNotification::getIsRead, true)
        );
    }

    /**
     * 发送通知（供其他 Service 调用）
     */
    public void sendNotification(Long userId, String title, String content, String type) {
        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setIsRead(false);
        notificationMapper.insert(notification);
    }
}
