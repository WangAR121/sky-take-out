package com.sky.mapper;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper{

    void insert(Orders orders);
    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);


    List<OrderVO> getOrders(OrdersPageQueryDTO ordersHistoryPageQueryDTO);
    @Select("select * from orders where id=#{id}")
    Orders getById(Long id);
    OrderStatisticsVO getStatistics();
    @Select("select * from orders where status= #{status} and order_time< #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    void updateList(List<Orders> ordersList);

    List<Map<String, Object>> orderSumMap(LocalDateTime startTime, LocalDateTime endTime);

    List<Map<String, Object>> getCompletedOrderNumberByTime(LocalDateTime startTime, LocalDateTime endTime);

    List<Map<String, Object>> getOrderNumber(LocalDateTime startTime, LocalDateTime endTime);

    List<Map<String, Object>> getTop10ByTime(LocalDateTime startTime, LocalDateTime endTime);

    Integer countByMap(Map map);

    Double sumByMap(Map map);
}
