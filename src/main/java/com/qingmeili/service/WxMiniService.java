package com.qingmeili.service;

/**
 * 微信小程序订阅消息与登录凭证换 openid。
 * 未配置 appid/secret 时所有方法安全降级（返回 false / null），不影响主流程。
 */
public interface WxMiniService {

    boolean configured();

    /** 小程序 wx.login 的 code 换 openid */
    String code2Openid(String jsCode);

    /**
     * 下发订阅消息。templateKey 取 shipped / refund / verified，对应 sys_config 里的模板 ID。
     * 用户未授权或未配置模板时返回 false，业务不中断。
     */
    boolean sendSubscribe(String openid, String templateKey, String thing, String remark, String page);
}
