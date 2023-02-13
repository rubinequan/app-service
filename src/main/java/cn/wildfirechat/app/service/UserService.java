package cn.wildfirechat.app.service;

import cn.wildfirechat.app.RestResult;
import cn.wildfirechat.app.pojo.FriendAlias;
import cn.wildfirechat.app.pojo.UserFriend;

public interface UserService {

    RestResult setUserFriend(UserFriend friend);

    RestResult updateFriendAlias(FriendAlias alias);
}
