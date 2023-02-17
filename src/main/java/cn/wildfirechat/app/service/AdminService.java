package cn.wildfirechat.app.service;

import cn.wildfirechat.app.RestResult;
import cn.wildfirechat.app.jpa.Report;
import cn.wildfirechat.app.pojo.AdminLogin;
import cn.wildfirechat.app.pojo.BlackListStatusPojo;
import cn.wildfirechat.app.pojo.UserGroupDel;

import java.util.List;

public interface AdminService {

    RestResult login(AdminLogin adminLogin);

    RestResult exit();

    RestResult updateReport(Report report);

    RestResult getReportList();

    RestResult ban(String uid);

    RestResult sensitiveList();

    RestResult sensitiveAdd(List<String> sensitives);

    RestResult delSensitive(List<String> sensitives);

    RestResult userList(String userid);

    RestResult delGroup(UserGroupDel del);

    RestResult groupList(String id);

    Object blacklist(BlackListStatusPojo pojo);

    Object userDestroy(String userId);
}
