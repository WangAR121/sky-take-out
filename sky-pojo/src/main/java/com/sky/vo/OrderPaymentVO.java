package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentVO implements Serializable {

    private String nonceStr; //随机字符串
    private String paySign; //签名
    private String timeStamp; //时间戳
    private String signType; //签名算法
    private String packageStr; //统一下单接口返回的 prepay_id 参数值
    /**
     * 是否模拟支付：true=后端没调真实微信支付，小程序不要再调 wx.requestPayment，
     * 直接调 /user/order/mockPay/{orderNumber} 即可完成支付。
     */
    private Boolean mock;

}
