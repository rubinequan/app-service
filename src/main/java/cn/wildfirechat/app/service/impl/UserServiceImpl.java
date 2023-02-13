package cn.wildfirechat.app.service.impl;

import cn.wildfirechat.app.RestResult;
import cn.wildfirechat.app.pojo.FriendAlias;
import cn.wildfirechat.app.pojo.UserFriend;
import cn.wildfirechat.app.service.UserService;
import cn.wildfirechat.sdk.RelationAdmin;

@org.springframework.stereotype.Service
public class UserServiceImpl implements UserService {


    @Override
    public RestResult setUserFriend(UserFriend friend) {
        try {
            if(friend.getIsFriend().equals("0")){
                RelationAdmin.setUserFriend(friend.getUserId(),friend.getTargetId(),true,"");
            }else{
                RelationAdmin.setUserFriend(friend.getUserId(),friend.getTargetId(),false,"");
            }
        }catch (Exception e){
            System.out.println("设置失败");
        }
        return RestResult.ok("绑定关系成功");
    }

    @Override
    public RestResult updateFriendAlias(FriendAlias alias) {
        try {
            RelationAdmin.updateFriendAlias(alias.getOperator(),alias.getTargetId(),alias.getAlias());
        }catch (Exception e){
            System.out.println("设置失败");
        }
        return RestResult.ok("修改好友备注成功");
    }
}
