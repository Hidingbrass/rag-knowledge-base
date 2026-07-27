package com.example.aikb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 为 Vue Router 的 History 模式提供前端入口回退。
 *
 * 用户刷新或直接访问前端页面时，Spring Boot 仍返回同一份 index.html，
 * 具体页面再由 Vue Router 在浏览器中决定。API 路径不经过这里。
 */
@Controller
public class FrontendController {

    @GetMapping({"/library", "/study", "/career"})
    public String forwardApplicationRoutes() {
        return "forward:/index.html";
    }
}
