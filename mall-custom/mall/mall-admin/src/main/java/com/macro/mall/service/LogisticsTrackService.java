package com.macro.mall.service;

import java.util.List;

/**
 * 承运商物流轨迹查询。渠道未接入时返回 available=false 并给出原因，
 * 由调用方如实展示，绝不返回伪造的扫描记录。
 */
public interface LogisticsTrackService {

    TrackResult query(String deliveryCompany, String deliverySn, String receiverPhone);

    record TrackNode(String time, String context) {
    }

    record TrackResult(boolean available, boolean success, String message, String state, String stateText,
                       List<TrackNode> nodes) {

        public static TrackResult unavailable(String message) {
            return new TrackResult(false, false, message, null, null, List.of());
        }

        public static TrackResult failed(String message) {
            return new TrackResult(true, false, message, null, null, List.of());
        }

        public static TrackResult success(String state, String stateText, List<TrackNode> nodes) {
            return new TrackResult(true, true, null, state, stateText, nodes);
        }
    }
}