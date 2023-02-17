package cn.wildfirechat.app.service.impl;

import cn.wildfirechat.app.RestResult;
import cn.wildfirechat.app.jpa.Report;
import cn.wildfirechat.app.jpa.ReportRepository;
import cn.wildfirechat.app.pojo.AdminLogin;
import cn.wildfirechat.app.pojo.BlackListStatusPojo;
import cn.wildfirechat.app.pojo.UserGroupDel;
import cn.wildfirechat.app.service.AdminService;
import cn.wildfirechat.pojos.InputOutputUserInfo;
import cn.wildfirechat.pojos.OutputUserStatus;
import cn.wildfirechat.sdk.GroupAdmin;
import cn.wildfirechat.sdk.RelationAdmin;
import cn.wildfirechat.sdk.SensitiveAdmin;
import cn.wildfirechat.sdk.UserAdmin;
import cn.wildfirechat.sdk.model.IMResult;
import com.qiniu.util.Json;
import org.apache.logging.log4j.core.util.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private ReportRepository reportRepository;

    public static Integer loginStatus=0;

    @Override
    public RestResult login(AdminLogin adminLogin) {
        if(StringUtils.isEmpty(adminLogin.getUserName())){
            return RestResult.error(RestResult.RestCode.LOGIN_FAIL);
        }
        if(StringUtils.isEmpty(adminLogin.getPassword())){
            return RestResult.error(RestResult.RestCode.LOGIN_FAIL);
        }
        if(adminLogin.getUserName().equals("admin") && adminLogin.getPassword().equals("admin")){
            AdminServiceImpl.loginStatus=200;
            return RestResult.ok("登录成功");
        }
        return RestResult.error(RestResult.RestCode.LOGIN_ERROR);
    }

    @Override
    public RestResult exit() {
        AdminServiceImpl.loginStatus=0;
        return RestResult.ok("退出成功");
    }

    @Override
    public RestResult updateReport(Report report) {
        reportRepository.updateById(report.getStatus(),report.getTid());
        return RestResult.ok("修改成功");
    }

    @Override
    public RestResult getReportList() {
        if(AdminServiceImpl.loginStatus!=200){
            return RestResult.error(RestResult.RestCode.LOGIN_NO);
        }
        return RestResult.ok(reportRepository.findAll());
    }

    @Override
    public RestResult ban(String uid) {
        if(AdminServiceImpl.loginStatus!=200){
            return RestResult.error(RestResult.RestCode.LOGIN_NO);
        }

        try {
            UserAdmin.updateUserBlockStatus(uid,2);
        }catch (Exception e){
            return RestResult.ok("封禁失败，用户id不存在");
        }

        return RestResult.ok("封禁成功");
    }

    @Override
    public RestResult sensitiveList() {
        if(AdminServiceImpl.loginStatus!=200){
            return RestResult.error(RestResult.RestCode.LOGIN_NO);
        }
        try {
            return RestResult.ok(SensitiveAdmin.getSensitives());
        }catch (Exception e){
            return RestResult.ok(new ArrayList<>());
        }
    }

    @Override
    public RestResult sensitiveAdd(List<String> sensitives) {
        if(AdminServiceImpl.loginStatus!=200){
            return RestResult.error(RestResult.RestCode.LOGIN_NO);
        }
        try {
            return RestResult.ok(SensitiveAdmin.addSensitives(sensitives));
        }catch (Exception e){
            return RestResult.error(RestResult.RestCode.ADD_FAIL);
        }
    }

    @Override
    public RestResult delSensitive(List<String> sensitives) {
        if(AdminServiceImpl.loginStatus!=200){
            return RestResult.error(RestResult.RestCode.LOGIN_NO);
        }
        try {
            return RestResult.ok(SensitiveAdmin.removeSensitives(sensitives));
        }catch (Exception e){
            return RestResult.error(RestResult.RestCode.DEL_FAIL);
        }
    }

    @Override
    public RestResult userList(String userid) {
        try {
            return RestResult.ok(RelationAdmin.getFriendList(userid));
        }catch (Exception e){
            return RestResult.ok(new ArrayList<>());
        }
    }

    @Override
    public RestResult delGroup(UserGroupDel del) {
        try {
            return RestResult.ok(GroupAdmin.dismissGroup(del.getOperator(),del.getGroupId(),del.getToLines(),del.getNotifyMessage()));
        }catch (Exception e){
            return RestResult.ok(new ArrayList<>());
        }
    }

    @Override
    public RestResult groupList(String id) {
        try {
            return RestResult.ok(GroupAdmin.getGroupMembers(id));
        }catch (Exception e){
            return RestResult.ok(new ArrayList<>());
        }
    }

    @Override
    public Object blacklist(BlackListStatusPojo pojo) {
        try {
            return RestResult.ok(RelationAdmin.setUserBlacklist(pojo.getUserId(), pojo.getTargetUid(), pojo.getStatus()));
        } catch (Exception e) {
            return RestResult.error(RestResult.RestCode.BLACKLIST_FAIL);
        }
    }

    @Override
    public Object userDestroy(String userId) {
        try {
            IMResult<Void> voidIMResult = UserAdmin.destroyUser(userId);
            return RestResult.ok(voidIMResult);
        } catch (Exception e) {
            return RestResult.error(RestResult.RestCode.BLACKLIST_FAIL);
        }
    }
}
