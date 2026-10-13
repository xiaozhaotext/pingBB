package com.macro.mall.service;

import com.macro.mall.config.LogisticsTrackProperties;
import com.macro.mall.service.LogisticsTrackService.TrackResult;
import com.macro.mall.service.impl.LogisticsTrackServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 物流轨迹：未接入/未识别必须如实返回，接入后按承运商响应解析轨迹节点。 */
class LogisticsTrackServiceImplTest {

    /** 用固定响应替换真实承运商调用，保证测试不出网。 */
    private static class StubService extends LogisticsTrackServiceImpl {
        private String body;
        private Exception error;

        @Override
        protected String postToChannel(String paramText, String sign) {
            if (error != null) {
                throw new IllegalStateException(error);
            }
            return body;
        }
    }

    private final LogisticsTrackProperties properties = new LogisticsTrackProperties();
    private final StubService service = new StubService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "properties", properties);
    }

    private void enableChannel() {
        properties.setEnabled(true);
        properties.setCustomer("customer-1");
        properties.setKey("secret-key");
    }

    @Test
    void blankTrackingNumberIsRejected() {
        TrackResult result = service.query("圆通", " ", null);
        assertTrue(result.available());
        assertFalse(result.success());
    }

    @Test
    void disabledChannelIsReportedAsUnavailable() {
        TrackResult result = service.query("圆通", "YT123456", null);
        assertFalse(result.available());
        assertTrue(result.message().contains("未启用"));
    }

    @Test
    void missingCredentialsAreReportedAsUnavailable() {
        properties.setEnabled(true);
        TrackResult result = service.query("圆通", "YT123456", null);
        assertFalse(result.available());
        assertTrue(result.message().contains("凭证未配置"));
    }

    @Test
    void unknownCarrierIsReportedAsUnavailable() {
        enableChannel();
        TrackResult result = service.query("某不知名快递", "YT123456", null);
        assertFalse(result.available());
        assertTrue(result.message().contains("未识别"));
    }

    @Test
    void channelResponseIsParsedIntoNodes() {
        enableChannel();
        service.body = "{\"message\":\"ok\",\"state\":\"3\",\"data\":["
                + "{\"ftime\":\"2026-10-13 10:00:00\",\"context\":\"已签收\"},"
                + "{\"ftime\":\"2026-10-13 08:00:00\",\"context\":\"派件中\"}]}";
        TrackResult result = service.query("圆通速递", "YT123456", "13800000000");
        assertTrue(result.success());
        assertEquals("已签收", result.stateText());
        assertEquals(2, result.nodes().size());
        assertEquals("已签收", result.nodes().get(0).context());
    }

    @Test
    void carrierErrorIsSurfacedHonestly() {
        enableChannel();
        service.body = "{\"result\":false,\"returnCode\":\"408\",\"message\":\"查询无结果\"}";
        TrackResult result = service.query("YT", "YT123456", null);
        assertTrue(result.available());
        assertFalse(result.success());
        assertEquals("查询无结果", result.message());
    }

    @Test
    void channelExceptionIsSurfacedHonestly() {
        enableChannel();
        service.error = new IllegalStateException("timeout");
        TrackResult result = service.query("YT", "YT123456", null);
        assertTrue(result.available());
        assertFalse(result.success());
        assertTrue(result.message().contains("查询失败"));
    }
}