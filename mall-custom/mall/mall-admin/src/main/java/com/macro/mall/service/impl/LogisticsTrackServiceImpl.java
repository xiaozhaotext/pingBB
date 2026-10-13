package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.macro.mall.config.LogisticsTrackProperties;
import com.macro.mall.service.LogisticsTrackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 快递100 实时物流轨迹查询实现。
 * 单号来自订单真实发货记录，不缓存、不伪造；凭证缺失时返回 available=false。
 */
@Slf4j
@Service
public class LogisticsTrackServiceImpl implements LogisticsTrackService {

    /** 常见承运商名称到快递100编码的映射，存储值本身为编码时直接透传。 */
    private static final Map<String, String> COMPANY_CODES = new LinkedHashMap<>();

    static {
        COMPANY_CODES.put("顺丰", "shunfeng");
        COMPANY_CODES.put("顺丰速运", "shunfeng");
        COMPANY_CODES.put("圆通", "yuantong");
        COMPANY_CODES.put("圆通速递", "yuantong");
        COMPANY_CODES.put("中通", "zhongtong");
        COMPANY_CODES.put("申通", "shentong");
        COMPANY_CODES.put("韵达", "yunda");
        COMPANY_CODES.put("韵达速递", "yunda");
        COMPANY_CODES.put("邮政", "youzhengguonei");
        COMPANY_CODES.put("中国邮政", "youzhengguonei");
        COMPANY_CODES.put("EMS", "ems");
        COMPANY_CODES.put("京东", "jd");
        COMPANY_CODES.put("京东物流", "jd");
        COMPANY_CODES.put("百世", "huitongkuaidi");
        COMPANY_CODES.put("百世快递", "huitongkuaidi");
        COMPANY_CODES.put("天天", "tiantian");
        COMPANY_CODES.put("德邦", "debangwuliu");
        COMPANY_CODES.put("德邦物流", "debangwuliu");
        COMPANY_CODES.put("极兔", "jtexpress");
        COMPANY_CODES.put("极兔速递", "jtexpress");
        COMPANY_CODES.put("宅急送", "zhaijisong");
        COMPANY_CODES.put("优速", "youshuwuliu");
        COMPANY_CODES.put("菜鸟", "cainiao");
    }

    private static final Map<String, String> STATE_TEXTS = Map.of(
            "0", "在途", "1", "揽收", "2", "疑难", "3", "已签收", "4", "退签",
            "5", "派件中", "6", "退回", "7", "转投", "8", "清关", "14", "拒签");

    @Autowired
    private LogisticsTrackProperties properties;

    @Override
    public TrackResult query(String deliveryCompany, String deliverySn, String receiverPhone) {
        if (StrUtil.isBlank(deliverySn)) {
            return TrackResult.failed("该订单暂无运单号");
        }
        if (!properties.isEnabled()) {
            return TrackResult.unavailable("物流轨迹查询通道未启用（mall.logistics.enabled=false）");
        }
        if (StrUtil.isBlank(properties.getCustomer()) || StrUtil.isBlank(properties.getKey())) {
            return TrackResult.unavailable("物流轨迹查询凭证未配置（mall.logistics.customer/key）");
        }
        String companyCode = resolveCompanyCode(deliveryCompany);
        if (companyCode == null) {
            return TrackResult.unavailable("未识别的快递公司：" + StrUtil.blankToDefault(deliveryCompany, "空")
                    + "，请维护承运商编码映射");
        }
        JSONObject param = new JSONObject();
        param.set("com", companyCode);
        param.set("num", deliverySn.trim());
        if (StrUtil.isNotBlank(receiverPhone)) {
            // 顺丰等承运商需要收件人手机号后四位才能查询
            param.set("phone", receiverPhone.trim());
        }
        String paramText = param.toString();
        String sign = DigestUtil.md5Hex(paramText + properties.getKey() + properties.getCustomer()).toUpperCase();
        try {
            String body = postToChannel(paramText, sign);
            return parseResponse(body);
        } catch (Exception e) {
            log.error("物流轨迹查询失败，运单号：{}，快递公司：{}", deliverySn, companyCode, e);
            return TrackResult.failed("物流轨迹查询失败：" + e.getMessage());
        }
    }

    /** 可覆盖的承运商调用，便于在隔离测试中替换为固定响应。 */
    protected String postToChannel(String paramText, String sign) {
        return HttpUtil.createPost(properties.getUrl())
                .form("customer", properties.getCustomer())
                .form("sign", sign)
                .form("param", paramText)
                .timeout(properties.getTimeoutMillis())
                .execute()
                .body();
    }

    private TrackResult parseResponse(String body) {
        if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
            return TrackResult.failed("物流轨迹服务返回内容无法解析");
        }
        JSONObject json = JSONUtil.parseObj(body);
        JSONArray data = json.getJSONArray("data");
        if (data != null) {
            List<TrackNode> nodes = new ArrayList<>();
            for (Object item : data) {
                JSONObject node = (JSONObject) item;
                nodes.add(new TrackNode(node.getStr("ftime", node.getStr("time", "")), node.getStr("context", "")));
            }
            String state = json.getStr("state");
            return TrackResult.success(state, STATE_TEXTS.getOrDefault(state, "未知状态"), nodes);
        }
        String message = json.getStr("message");
        return TrackResult.failed(StrUtil.blankToDefault(message, "物流轨迹查询失败"));
    }

    /** 承运商编码：已是字母编码直接透传，中文名称按内置表映射，未识别返回 null。 */
    private String resolveCompanyCode(String deliveryCompany) {
        if (StrUtil.isBlank(deliveryCompany)) {
            return null;
        }
        String value = deliveryCompany.trim();
        if (value.matches("^[A-Za-z0-9_]+$")) {
            return value.toLowerCase();
        }
        return COMPANY_CODES.get(value);
    }
}
