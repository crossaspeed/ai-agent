package com.it.ai.aiagent.controller;

import com.it.ai.aiagent.bean.StudyPlanCreateRequest;
import com.it.ai.aiagent.bean.StudyReminderTaskView;
import com.it.ai.aiagent.service.StudyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "学习计划提醒")
@RestController
@RequestMapping("/agent/study-plan")
@org.springframework.web.bind.annotation.CrossOrigin(origins = "*")
public class StudyPlanController {

    @Autowired
    private StudyPlanService studyPlanService;

    @Operation(summary = "创建接下来一周学习计划")
    @PostMapping("/weekly")
    public Map<String, Object> createWeeklyPlan(@RequestBody StudyPlanCreateRequest request) {
        return studyPlanService.createWeeklyPlan(request);
    }

    @Operation(summary = "查询接下来几天学习任务")
    @GetMapping("/tasks")
    public List<StudyReminderTaskView> getTasks(@RequestParam(value = "days", defaultValue = "14") int days) {
        return studyPlanService.getUpcomingTasks(days);
    }

    @Operation(summary = "启用或停用单个任务")
    @PatchMapping("/tasks/{id}/status")
    public Map<String, Object> updateTaskStatus(@PathVariable("id") Long id,
                                                 @RequestParam("enabled") boolean enabled) {
        return studyPlanService.updateTaskStatus(id, enabled);
    }

    @Operation(summary = "发送测试提醒")
    @PostMapping("/tasks/{id}/test")
    public Map<String, Object> testReminder(@PathVariable("id") Long id) {
        return studyPlanService.sendTestReminder(id);
    }
}
