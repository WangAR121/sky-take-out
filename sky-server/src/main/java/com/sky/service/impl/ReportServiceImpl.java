package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
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

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
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
}
