package com.qsld.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qsld.dto.LoginFormDTO;
import com.qsld.dto.Result;
import com.qsld.dto.UserDTO;
import com.qsld.entity.User;
import com.qsld.mapper.UserMapper;
import com.qsld.service.IUserService;
import com.qsld.utils.RegexUtils;
import com.qsld.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.qsld.utils.RedisConstants.*;
import static com.qsld.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到session
        //session.setAttribute("code", code);
        // 4.保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.info("发送短信验证码成功，验证码为：{}", code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();

        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //2.从session获取验证码并校验
        //String cacheCode = (String) session.getAttribute("code");
        //2.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            //3.不一致，报错
            return Result.fail("验证码错误");
        }

        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if (user == null) {
            //6.不存在，则创建新用户并保存
            user = createUserWithPhone(phone);
        }
        //7.存在，保存用户信息到session
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        // 7.存在，保存用户信息到redis
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.将user对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3.存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8.返回token
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        //删除Token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return Result.fail("退出失败");
        }
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        //删除ThreadLocal中的用户信息
        UserHolder.removeUser();
        return Result.ok("退出成功");
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2.保存用户
        save(user);
        return user;
    }
}
