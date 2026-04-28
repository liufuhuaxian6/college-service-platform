package com.ruc.college.module.student.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruc.college.common.log.OperationLog;
import com.ruc.college.common.result.Result;
import com.ruc.college.common.security.RequireRole;
import com.ruc.college.module.auth.entity.SysUser;
import com.ruc.college.module.student.entity.StudentHonor;
import com.ruc.college.module.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    // ==================== 学生端 ====================

    @GetMapping("/profile")
    public Result<Map<String, Object>> myProfile() {
        return Result.ok(studentService.getMyProfile());
    }

    @GetMapping("/honors")
    public Result<List<StudentHonor>> myHonors() {
        return Result.ok(studentService.getMyHonors());
    }

    // ==================== 管理端 ====================

    @GetMapping("/page")
    @RequireRole(minLevel = 3)
    public Result<Page<SysUser>> studentPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String className) {
        return Result.ok(studentService.getStudentPage(page, size, grade, major, className));
    }

    @GetMapping("/{id}/detail")
    @RequireRole(minLevel = 2)
    public Result<Map<String, Object>> studentDetail(@PathVariable Long id) {
        return Result.ok(studentService.getStudentDetail(id));
    }

    @PostMapping("/{id}/honor")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "学生画像", action = "录入荣誉")
    public Result<Map<String, Object>> addHonor(@PathVariable Long id, @RequestBody StudentHonor honor) {
        Long honorId = studentService.addHonor(id, honor);
        return Result.ok(Map.of("id", honorId));
    }

    @PutMapping("/honor/{honorId}")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "学生画像", action = "修改荣誉")
    public Result<Void> updateHonor(@PathVariable Long honorId, @RequestBody StudentHonor honor) {
        studentService.updateHonor(honorId, honor);
        return Result.ok();
    }

    @DeleteMapping("/honor/{honorId}")
    @RequireRole(minLevel = 2)
    @OperationLog(module = "学生画像", action = "删除荣誉")
    public Result<Void> deleteHonor(@PathVariable Long honorId) {
        studentService.deleteHonor(honorId);
        return Result.ok();
    }
}
