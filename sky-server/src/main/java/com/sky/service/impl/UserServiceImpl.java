package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    //微信服务接口地址
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";
    @Autowired
    public WeChatProperties weChatProperties;
    @Autowired
    private UserMapper userMapper;

    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        String openid = getOPenid(userLoginDTO.getCode());
        if (openid==null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        User user= userMapper.getByOpenid(openid);
        if(user==null){
           user=User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
           userMapper.insert(user);
        }
        return user;
    }
    private String getOPenid(String code){
        Map<String,String> Map = new HashMap<>();
        Map.put("appid",weChatProperties.getAppid());
        Map.put("secret",weChatProperties.getSecret());
        Map.put("js_code",code);
        Map.put("grant_type","authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN, Map);
        log.info("调用微信登录接口返回：{}", json);
        if (json == null || json.isEmpty()) {
            log.error("微信登录接口无响应，请检查网络或 api.weixin.qq.com 是否可达");
            return null;
        }
        JSONObject jsonObject = JSON.parseObject(json);
        Integer errcode = jsonObject.getInteger("errcode");
        if (errcode != null) {
            // 常见：40013 AppID 无效、40125 AppSecret 无效、40029 code 无效/已使用、40163 code 已被使用
            log.error("微信登录失败：errcode={}, errmsg={}", errcode, jsonObject.getString("errmsg"));
            return null;
        }
        return jsonObject.getString("openid");
    }
}
