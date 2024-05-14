package com.ysdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ysdp.dto.LoginFormDTO;
import com.ysdp.dto.Result;
import com.ysdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {
    /**
     * 发送短信验证码并保存验证码
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 用户登录（基于验证码或密码登录）
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 用户签到
     * @return
     */
    Result sign();

    /**
     * 用户连续签到统计
     * @return
     */
    Result signCount();

    /**
     * 退出功能
     * @return
     */
    Result logout(String tokenKey);
}
