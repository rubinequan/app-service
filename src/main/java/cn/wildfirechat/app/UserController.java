package cn.wildfirechat.app;

import cn.wildfirechat.app.pojo.FriendAlias;
import cn.wildfirechat.app.pojo.UserFriend;
import cn.wildfirechat.app.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @PostMapping(value = "/updateUserFriend", produces = "application/json;charset=UTF-8")
    public Object updateUserFriend(@RequestBody UserFriend request) {
        return userService.setUserFriend(request);
    }

    @PostMapping(value = "/updateFriendAlias", produces = "application/json;charset=UTF-8")
    public Object updateFriendAlias(@RequestBody FriendAlias request) {
        return userService.updateFriendAlias(request);
    }

}
