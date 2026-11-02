package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.properties.WeChatProperties;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
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

}
