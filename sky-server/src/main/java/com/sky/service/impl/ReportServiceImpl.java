package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        LocalDate originalBegin = begin;
        List<String> dateStrList = new ArrayList<>();
        Map<String, BigDecimal> turnoverMap = new HashMap<>();

        while (!begin.isAfter(end)) {
            String dateStr = begin.toString();
            dateStrList.add(dateStr);
            turnoverMap.put(dateStr, BigDecimal.ZERO);
            begin = begin.plusDays(1);
        }
        LocalDateTime startTime = LocalDateTime.of(originalBegin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<Map<String,Object>> mapList = orderMapper.orderSumMap(startTime, endTime);

        mapList.forEach(row -> {
            String day = row.get("day").toString();
            BigDecimal val = (BigDecimal) row.get("turnover");
            if(val == null){
                val = BigDecimal.ZERO;
            }
            turnoverMap.put(day, val);
        });

        List<String> turnStrList = new ArrayList<>();
        for (String d : dateStrList) {
            turnStrList.add(turnoverMap.get(d).toString());
        }

        String dateJoin = String.join(",", dateStrList);
        String turnoverJoin = String.join(",", turnStrList);

        return TurnoverReportVO.builder()
                .dateList(dateJoin)
                .turnoverList(turnoverJoin)
                .build();
    }

    @Override
    public UserReportVO getuserStatistics(LocalDate begin, LocalDate end) {
        LocalDate originalBegin = begin;
        List<String> dateStrList = new ArrayList<>();
        Map<String, Long> increaseUserMap = new HashMap<>();
        List<Long> ttUserList = new ArrayList<>();
        List<Long> inUserList = new ArrayList<>();
        while (!begin.isAfter(end)) {
            String dateStr = begin.toString();
            dateStrList.add(dateStr);
            increaseUserMap.put(dateStr,0L);
            begin = begin.plusDays(1);
        }
        LocalDateTime startTime = LocalDateTime.of(originalBegin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<Map<String,Long>> increaseUserList= userMapper.getIncreaseUserByTime(startTime,endTime);
        Long Total=userMapper.getNumber(startTime);
        for(Map<String,Long> user: increaseUserList){
            String day = user.get("day").toString();
            Long number = user.get("number");
            increaseUserMap.put(day,number);
        }
        for(String date:dateStrList){
            Long up = increaseUserMap.get(date);
            inUserList.add(up);
            Total+=up;
            ttUserList.add(Total);
        }
        String dateList=String.join(",",dateStrList);
        String totalUserList=ttUserList.stream().map(Object::toString).collect(Collectors.joining(","));
        String newUserList=inUserList.stream().map(Object::toString).collect(Collectors.joining(","));
        return UserReportVO.builder()
                .dateList(dateList)
                .totalUserList(totalUserList)
                .newUserList(newUserList)
                .build();
    }
}
