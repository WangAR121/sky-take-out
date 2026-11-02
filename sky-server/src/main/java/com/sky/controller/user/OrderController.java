package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "用户端订单相关接口")
@Slf4j
public class OrderController {
    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO){
        log.info("用户下单，参数为{}",ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO=orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付：真实微信支付时返回调起 wx.requestPayment 的参数；
     * 开启模拟支付(sky.wechat.mock-pay=true)时返回 mock=true 的假参数，不会请求微信服务器。
     */
    // 课程配套的微信小程序（uni-app 编译产物）固定用 PUT 调这个接口，这里同时兼容 PUT 和 POST
    @RequestMapping(value = "/payment", method = {RequestMethod.POST, RequestMethod.PUT})
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 模拟支付成功：小程序发现 payment 返回 mock=true 后调用本接口完成支付，
     * 内部走的是和微信支付回调相同的 paySuccess 逻辑。关闭模拟支付后本接口不可用。
     */
    @PostMapping("/mockPay/{orderNumber}")
    @ApiOperation("模拟支付成功（本地开发用，跳过真实微信支付）")
    public Result mockPay(@PathVariable String orderNumber) {
        log.info("模拟支付成功，订单号：{}", orderNumber);
        orderService.mockPaySuccess(orderNumber);
        return Result.success();
    }
}