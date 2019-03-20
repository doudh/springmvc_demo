package com.doudh.modules.service;

import com.doudh.annotation.MyService;

@MyService(value = "userService")
public class UserService {
    public String login(String username, String password){
        if("admin".equals(username) && "123456".equals(password)){
            return "登录成功  "+"用户名："+username +"   密码："+password;
        }
        return "登录失败   "+"用户名密码错误";
    }
}
