package cn.wildfirechat.app;

import cn.wildfirechat.app.jpa.Report;
import cn.wildfirechat.app.pojo.*;
import cn.wildfirechat.app.service.AdminService;
import cn.wildfirechat.app.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminController {
    private static final Logger LOG = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private AdminService adminService;

    @PostMapping(value = "/admin/login", produces = "application/json;charset=UTF-8")
    public Object login(@RequestBody AdminLogin request) {
        return adminService.login(request);
    }

    @PostMapping(value = "/admin/exit", produces = "application/json;charset=UTF-8")
    public Object exit() {
        return adminService.exit();
    }

    @PostMapping(value = "/admin/getReportList", produces = "application/json;charset=UTF-8")
    public Object getReportList() {
        return adminService.getReportList();
    }

    @PostMapping(value = "/admin/updateReport", produces = "application/json;charset=UTF-8")
    public Object updateReport(@RequestBody Report report) {
        return adminService.updateReport(report);
    }

    @PostMapping(value = "/admin/ban", produces = "application/json;charset=UTF-8")
    public Object ban(@RequestBody ReportRequest response) {
        return adminService.ban(response.getUid());
    }

    @PostMapping(value = "/admin/sensitiveList", produces = "application/json;charset=UTF-8")
    public Object sensitiveList() {
        return adminService.sensitiveList();
    }

    @PostMapping(value = "/admin/sensitiveAdd", produces = "application/json;charset=UTF-8")
    public Object sensitiveAdd(@RequestBody SensitiveRequest request) {
        return adminService.sensitiveAdd(request.getSensitives());
    }

    @PostMapping(value = "/admin/delSensitive", produces = "application/json;charset=UTF-8")
    public Object delSensitive(@RequestBody SensitiveRequest request) {
        return adminService.delSensitive(request.getSensitives());
    }

    @PostMapping(value = "/admin/userList", produces = "application/json;charset=UTF-8")
    public Object userList(@RequestBody UserFriend request) {
        return adminService.userList(request.getUserId());
    }

    @PostMapping(value = "/admin/delGroup", produces = "application/json;charset=UTF-8")
    public Object delGroup(@RequestBody UserGroupDel del) {
        return adminService.delGroup(del);
    }

    @PostMapping(value = "/admin/groupList", produces = "application/json;charset=UTF-8")
    public Object groupList(@RequestBody IdPojo pojo) {
        return adminService.groupList(pojo.getId());
    }

}
