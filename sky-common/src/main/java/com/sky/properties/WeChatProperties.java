package com.sky.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.wechat")
@Data
public class WeChatProperties {

    private String appid; //小程序的appid
    private String secret; //小程序的秘钥
    private String mchid; //商户号
    private String mchSerialNo; //商户API证书的证书序列号
    private String privateKeyFilePath; //商户私钥文件
    private String apiV3Key; //证书解密的密钥
    private String weChatPayCertFilePath; //平台证书
    private String notifyUrl; //支付成功的回调地址
    private String refundNotifyUrl; //退款成功的回调地址
    /**
     * 是否开启模拟支付：true 时不调用微信支付，直接返回假的调起支付参数，
     * 由小程序调用 /user/order/mockPay/{orderNumber} 完成"支付"。
     * 本地没有企业资质、无法开通微信支付时使用；拿到资质后改成 false 即可无缝切回真实支付。
     */
    private Boolean mockPay = false;

}
