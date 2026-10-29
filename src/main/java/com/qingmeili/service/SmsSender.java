package com.qingmeili.service;

/**
 * 短信发送通道。
 * demo：只记日志（演示环境，验证码固定 123456）；
 * webhook：POST JSON 到网关（阿里云/腾讯云/自建网关均可转发），失败不阻塞登录。
 */
public interface SmsSender {

    boolean sendCode(String mobile, String code);
}
