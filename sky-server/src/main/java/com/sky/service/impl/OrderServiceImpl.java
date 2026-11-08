package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.properties.WeChatProperties;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.websocket.WebSocketServer;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private WebSocketServer webSocketServer;
    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.gaode.ak}")
    private String ak;

    private Map<Long, String> getOrderDishesStr(List<OrderVO> orderVOList) {
        // 列表为空时直接返回：否则 ids 为空集合，会拼出 `where order_id in ()` 这类非法 SQL
        if (orderVOList == null || orderVOList.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> ids=new ArrayList<>();
        orderVOList.forEach(orderVO -> {
            ids.add(orderVO.getId());
        });
        // 查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderIds(ids);

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        Map<Long,String> orderDishListStr=orderDetailList.stream().collect(Collectors.groupingBy(OrderDetail::getOrderId, Collectors.mapping(o -> {
            return beStr((OrderDetail)o);
        },Collectors.joining())));

        // 将该订单对应的所有菜品信息拼接在一起
        return orderDishListStr;
    }
    private String beStr(OrderDetail orderDetail){
        String orderDish = orderDetail.getName() + "*" + orderDetail.getNumber() + ";";
        return orderDish;
    }
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","JSON");
        map.put("key",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://restapi.amap.com/v3/geocode/geo", map);
        JSONObject jsonObject = JSON.parseObject(shopCoordinate);

        if(!"1".equals(jsonObject.getString("status"))){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        // 地理编码是geocodes数组
        JSONArray geocodes = jsonObject.getJSONArray("geocodes");
        if(geocodes == null || geocodes.isEmpty()){
            throw new OrderBusinessException("店铺地址无法定位");
        }
        JSONObject geoItem = geocodes.getJSONObject(0);
        String location = geoItem.getString("location");
        String[] lngLatArr = location.split(",");
        String lng = lngLatArr[0];
        String lat = lngLatArr[1];
        //店铺经纬度坐标
        String shopLngLat = lng + "," + lat;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://restapi.amap.com/v3/geocode/geo", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("1")){
            throw new OrderBusinessException("收货地址解析失败");
        }


        JSONArray userGeocodes = jsonObject.getJSONArray("geocodes");
        if(userGeocodes == null || userGeocodes.isEmpty()){
            throw new OrderBusinessException("收货地址无法定位");
        }
        location = userGeocodes.getJSONObject(0).getString("location");
        lngLatArr = location.split(",");
        lng = lngLatArr[0];
        lat = lngLatArr[1];
        //用户收货地址经纬度坐标
        String userLngLat = lng + "," + lat;

        Map dirMap = new HashMap();
        dirMap.put("key", ak);
        dirMap.put("origin", shopLngLat);
        dirMap.put("destination", userLngLat);
        dirMap.put("nosteps", "1");
        dirMap.put("type","0");//0驾车
        String json = HttpClientUtil.doGet("https://restapi.amap.com/v3/direction/driving", dirMap);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("1")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject route = jsonObject.getJSONObject("route");
        JSONArray jsonArray = (JSONArray) route.get("paths");
        Integer distance = ((JSONObject) jsonArray.get(0)).getInteger("distance");
        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        checkOutOfRange(addressBook.getCityName()+addressBook.getDistrictName()+addressBook.getDetail());
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .build();
        List<ShoppingCart> shoppingCartList= shoppingCartMapper.list(shoppingCart);
        if(shoppingCartList==null||shoppingCartList.isEmpty()){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setPayStatus(Orders.UN_PAID);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(BaseContext.getCurrentId());
        orders.setAddress(addressBook.getDetail());

        orderMapper.insert(orders);
        List<OrderDetail> orderDetailList=new ArrayList<>();
        shoppingCartList.forEach(shoppingCart1 -> {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(shoppingCart1,orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        });
        orderDetailMapper.insertBatch(orderDetailList);
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
        OrderSubmitVO orderSubmitVo = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
                .build();
        return orderSubmitVo;
    }
    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new OrderBusinessException("用户不存在");
        }

        // 支付前校验：订单必须存在、属于当前用户、处于"待付款"状态且未支付过
        Orders ordersDB = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!userId.equals(ordersDB.getUserId())) {
            throw new OrderBusinessException("只能支付自己的订单");
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            throw new OrderBusinessException("该订单已支付");
        }
        if (!Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //调用微信支付接口，生成预支付交易单（开启模拟支付时 WeChatPayUtil 内部直接返回假参数）
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));
        // 模拟支付标记：true 时小程序不要调 wx.requestPayment
        vo.setMock(jsonObject.getBoolean("mock"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        // 容错：真实回调可能重复到达，也可能是找不到的订单，直接记录日志返回，避免抛异常导致微信一直重试
        if (ordersDB == null) {
            log.warn("支付成功通知：订单不存在，orderNumber={}", outTradeNo);
            return;
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            log.info("支付成功通知：订单已支付，忽略重复通知，orderNumber={}", outTradeNo);
            return;
        }

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .payMethod(1) //支付方式 1微信，2支付宝
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // 来单提醒：支付成功后推送给管理端浏览器（前端 onmessage 里按 type 区分并播放提示音）
        // type=1 来单提醒，type=2 客户催单
        Map pushMessage = new HashMap();
        pushMessage.put("type", 1);
        pushMessage.put("orderId", orders.getId());
        pushMessage.put("content", "订单号：" + ordersDB.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(pushMessage));
    }

    /**
     * 模拟支付成功：本地没有企业资质时，代替微信支付回调完成订单状态流转。
     * 内部复用 paySuccess，保证和真实支付走的是同一套"已支付"逻辑，
     * 订单状态定时任务、接单、派送等后续功能不受影响。
     */
    @Override
    public void mockPaySuccess(String orderNumber) {
        // 只有明确开启模拟支付时才允许调用，防止这条接口被当成"免费下单"漏洞
        if (!Boolean.TRUE.equals(weChatProperties.getMockPay())) {
            throw new OrderBusinessException("模拟支付未开启，请使用真实微信支付");
        }
        Long userId = BaseContext.getCurrentId();
        Orders ordersDB = orderMapper.getByNumber(orderNumber);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!userId.equals(ordersDB.getUserId())) {
            throw new OrderBusinessException("只能操作自己的订单");
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            throw new OrderBusinessException("该订单已支付");
        }
        if (!Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        log.info("模拟支付成功：orderNumber={}", orderNumber);
        paySuccess(orderNumber);
    }

    @Override
    public PageResult OrdersHistory(OrdersPageQueryDTO ordersHistoryPageQueryDTO) {
        ordersHistoryPageQueryDTO.setUserId(BaseContext.getCurrentId());
        PageHelper.startPage(ordersHistoryPageQueryDTO.getPage(),ordersHistoryPageQueryDTO.getPageSize());
        List<OrderVO> ordersHistoryVOList=orderMapper.getOrders(ordersHistoryPageQueryDTO);
        List<Long> orderIds=ordersHistoryVOList.stream().map(OrderVO::getId).collect(Collectors.toList());
        Map<Long,List<OrderDetail>> orderDetailMap=new HashMap<>();
        if(!orderIds.isEmpty()){
            List<OrderDetail> orderDetailList=orderDetailMapper.getByOrderIds(orderIds);
            Map<Long,List<OrderDetail>> tempMap=orderDetailList.stream().collect(Collectors.groupingBy(OrderDetail::getOrderId));
            orderDetailMap.putAll(tempMap);
        }
        ordersHistoryVOList.forEach(ordersHistoryVO -> {
            ordersHistoryVO.setOrderDetailList(orderDetailMap.getOrDefault(ordersHistoryVO.getId(), Collections.emptyList()));
        });
        PageInfo<OrderVO> page=new PageInfo<>(ordersHistoryVOList);
        return new  PageResult(page.getTotal(),page.getList());
    }

    @Override
    public OrderVO getOrderDetail(Long id) {
        if (id==null){
            throw new OrderBusinessException(MessageConstant.ORDER_ID_NULL);
        }
        Orders orders = orderMapper.getById(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setUserId(BaseContext.getCurrentId());
        List<Long> ids=new ArrayList<>();
        ids.add(id);
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderIds(ids);
        orderVO.setOrderDetailList(orderDetails);
        return orderVO;
    }

    @Override
    public void cancelOrder(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 订单处于待接单状态下取消，需要进行退款
        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            orders.setPayStatus(Orders.REFUND);
        }
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    @Override
    public void repetition(Long id) {
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderIds(ids);
        List<ShoppingCart> shoppingCartList=orderDetailList.stream().map(orderDetail -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail,shoppingCart,"id");
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        List<OrderVO> orderVOList = orderMapper.getOrders(ordersPageQueryDTO);
        Map<Long,String> orderVOListMap=getOrderDishesStr(orderVOList);
        orderVOList.forEach(orderVO -> {
            orderVO.setOrderDishes(orderVOListMap.get(orderVO.getId()));
        });
        Page<OrderVO> page = (Page<OrderVO>)orderVOList;
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO orderStatisticsVO=orderMapper.getStatistics();
        return orderStatisticsVO;
    }

    @Override
    public void acceptOrder(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        // 订单只有存在且状态为2（待接单）才可以拒单
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (Objects.equals(payStatus, Orders.PAID)) {
            log.info("退款");
        }
        Orders orders = Orders.builder()
                .cancelTime(LocalDateTime.now())
                .id(ordersRejectionDTO.getId())
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .status(Orders.CANCELLED)
                .build();
        orderMapper.update(orders);
    }

    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == 1) {
            //用户已支付，需要退款
            log.info("退款");
        }

        // 管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    @Override
    public void delivery(Long id) {
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为3
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }

    @Override
    public void complete(Long id) {
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }
}
