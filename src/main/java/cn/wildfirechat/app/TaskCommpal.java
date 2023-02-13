package cn.wildfirechat.app;

import cn.wildfirechat.pojos.InputOutputUserInfo;
import cn.wildfirechat.sdk.UserAdmin;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class TaskCommpal {

    @Async
    public void updateUser(InputOutputUserInfo request){
        try {
            for (int i=1;i<10;i++){
                int num= (int)(Math.random()*(99 - 1 + 1) + 1);
                UserAdmin.updateUserInfo(request,num);
            }
        }catch (Exception e){
            System.out.println("修改失败："+e.getMessage());
        }

    }
}
