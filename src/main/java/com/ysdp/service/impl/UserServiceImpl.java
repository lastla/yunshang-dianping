package com.ysdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ysdp.dto.LoginFormDTO;
import com.ysdp.dto.Result;
import com.ysdp.dto.UserDTO;
import com.ysdp.entity.User;
import com.ysdp.mapper.UserMapper;
import com.ysdp.service.IUserService;
import com.ysdp.utils.RegexUtils;
import com.ysdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ysdp.utils.RedisConstants.*;
import static com.ysdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送短信验证码并保存验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号格式
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.不符合返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.符合，生成6位验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存到redis  //set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY +phone,code,
                LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);
        //返回ok
        return Result.ok();
    }
    /**
     * 用户登录（基于验证码或密码登录）
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //2.校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if(cacheCode==null || !cacheCode.equals(code)){
            //3.不一致，返回错误
            return Result.fail("验证码错误");
        }
        //4.根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if(user==null){
            //6，不存在，创建用户保存到数据库
            user = createUserWithPhone(phone);
        }

        //7.将用户信息保存到redis中     //hutool工具包
        //7.1 生成token            //简单字符串不带下划线
        String token = UUID.randomUUID().toString(true);
        // 7.2 将User转成Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true) //设置忽略null值
                        //设置将字段类型转为字符串  filedName为字段名，filedValue为字段值返回字段值即可
                        .setFieldValueEditor((filedName,filedValue)-> filedValue.toString()));
        //7.3 存储到Redis中
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //7.4设置token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //在拦截器中刷新token的有效期
        //8.返回token
        return Result.ok(token);
    }



    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomNumbers(10));
        //保存用户
        save(user);
        return user;
    }

    /**
     * 用户签到
     * @return
     */
    @Override
    public Result sign() {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //3.拼接key
        String key = USER_SIGN_KEY + userId + keySuffix;

        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    /**
     * 用户连续签到统计
     * @return
     */
    @Override
    public Result signCount() {

        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //3.拼接key
        String key = USER_SIGN_KEY + userId + keySuffix;

        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        //5.获取本月截止的所有签到记录  (返回的是十进制数字)  BITFIELD sign:5:202404 GET u9 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result==null || result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num==null || num==0){
            return Result.ok(0);
        }
        //6.循环遍历
        int count = 0;
        while (true){
            if((num & 1) == 0){//6.1. 让这个数字与1做与运算，得到最后一个bit位  判断这个bit位是否为0
                //6.3如果为0，说明未签到，结束
                break;
            }else { //6.4如果为1，说已签到 计算器加1
                count++;
            }
            //6.5把数字右移一位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result logout(String tokenKey) {
        stringRedisTemplate.opsForHash().delete(tokenKey,"nickName","icon","id");
        return Result.ok("退出登录成功！");
    }
}
