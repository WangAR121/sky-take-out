package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
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
    @Autowired
    private WorkspaceService workspaceService;
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

    @Override
    public OrderReportVO getordersStatistics(LocalDate begin, LocalDate end) {
        LocalDate originalBegin = begin;
        List<String> dateStrList = new ArrayList<>();
        List<Map<String, Object>> ordesCompletedMap = new ArrayList<>();
        List<Map<String, Object>> ordesMap = new ArrayList<>();
        Map<String, Integer> completedNumber = new HashMap<>();
        Map<String, Integer> AllNumber = new HashMap<>();
        while (!begin.isAfter(end)) {
            String dateStr = begin.toString();
            dateStrList.add(dateStr);
            completedNumber.put(dateStr,0);
            AllNumber.put(dateStr,0);
            begin = begin.plusDays(1);
        }
        LocalDateTime startTime = LocalDateTime.of(originalBegin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        ordesCompletedMap=orderMapper.getCompletedOrderNumberByTime(startTime,endTime);
        ordesMap=orderMapper.getOrderNumber(startTime,endTime);
        for(Map<String,Object> order: ordesCompletedMap){
            String day = order.get("day").toString();
            //MySQL 的 count(*) 是 BIGINT，MyBatis 映射成 Long，不能直接强转 Integer
            Integer number = order.get("number") == null ? 0 : ((Number) order.get("number")).intValue();
            completedNumber.put(day,number);
        }
        for(Map<String,Object> order: ordesMap){
            String day = order.get("day").toString();
            Integer number = order.get("number") == null ? 0 : ((Number) order.get("number")).intValue();
            AllNumber.put(day,number);
        }
        Integer completedAll=0;
        Integer ALLNumber=0;
        List<Integer> orderList=new ArrayList<>();
        List<Integer> orderComList=new ArrayList<>();
        for(String date:dateStrList){
            Integer completed = completedNumber.getOrDefault(date, 0);
            Integer number=AllNumber.getOrDefault(date, 0);
            orderList.add(number);
            orderComList.add(completed);
            completedAll+=completed;
            ALLNumber+=number;
        }
        // 注意：两个 Integer 直接相除是整除，会恒为 0，必须转成 double
        Double orderCompletionRate=ALLNumber==0?0.0:completedAll * 1.0 / ALLNumber;
        String dateList=String.join(",",dateStrList);
        String orderCountList=orderList.stream().map(Object::toString).collect(Collectors.joining(","));
        String validOrderCountList=orderComList.stream().map(Object::toString).collect(Collectors.joining(","));
        return OrderReportVO.builder()
                .dateList(dateList)
                .orderCountList(orderCountList)
                .validOrderCountList(validOrderCountList)
                .totalOrderCount(ALLNumber)
                .validOrderCount(completedAll)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    @Override
    public SalesTop10ReportVO getOrderTop10(LocalDate begin, LocalDate end) {
        LocalDateTime startTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<Map<String,Object>> Top10List=orderMapper.getTop10ByTime(startTime,endTime);
        List<String> dishList=Top10List.stream().map(map1->{
            return map1.get("dish").toString();
        }).collect(Collectors.toList());
        List<Long> orderNumber=Top10List.stream().map(map1->{
            Object number = map1.get("number");
            return number==null?0L:((Number)number).longValue();
        }).collect(Collectors.toList());
        String nameList=String.join(",",dishList);
        String numberList=orderNumber.stream().map(Object::toString).collect(Collectors.joining(","));
        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    @Override
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        LocalDate dateEnd = LocalDate.now().minusDays(1);
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN), LocalDateTime.of(dateEnd, LocalTime.MAX));
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            XSSFWorkbook excel = new XSSFWorkbook(in);
            XSSFSheet sheet = excel.getSheet("Sheet1");
            XSSFCell cell = sheet.getRow(1).getCell(1);
            cell.setCellValue("时间 "+dateBegin+"至"+dateEnd);
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());

            row=sheet.getRow(4);
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());
            ServletOutputStream out = response.getOutputStream();
            for (int i = 0; i < 30; i++) {
                LocalDate date=dateBegin.plusDays(i);
                BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(dateEnd, LocalTime.MAX));
                row=sheet.getRow(7+i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }
            excel.write(out);
            out.close();
            excel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
