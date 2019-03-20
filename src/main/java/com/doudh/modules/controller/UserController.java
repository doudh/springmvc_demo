package com.doudh.modules.controller;



import com.doudh.annotation.MyController;
import com.doudh.annotation.MyQualifier;
import com.doudh.annotation.MyRequestMapping;
import com.doudh.annotation.MyRequestParam;
import com.doudh.modules.service.UserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("/user")
public class UserController {

    @MyQualifier(value = "userService")
    private UserService userService;

    @MyRequestMapping("/login")
    public void login(HttpServletRequest request, HttpServletResponse response, @MyRequestParam("username") String username, @MyRequestParam("password") String password){
        try {
            request.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "text/html; charset=UTF-8");

            String info = userService.login(username, password);
            response.getWriter().write( info);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //测试地址  http://localhost:8080/user/login?username=admin&password=123456

}
