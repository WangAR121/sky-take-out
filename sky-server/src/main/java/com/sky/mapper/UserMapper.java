package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {
    @Select("select * from user where openid=#{openid}")
    User getByOpenid(String openid);

    void insert(User user);
    @Select("select * from user where id=#{userId}")
    User getById(Long userId);
    @Select("select date(create_time) day,count(*) number from user where create_time >= #{startTime} and create_time<#{endTime} group by date(create_time) ")
    List<Map<String, Long>> getIncreaseUserByTime(LocalDateTime startTime, LocalDateTime endTime);
    @Select("select count(*) from user where create_time<#{startTime}")
    Long getNumber(LocalDateTime startTime);
}
