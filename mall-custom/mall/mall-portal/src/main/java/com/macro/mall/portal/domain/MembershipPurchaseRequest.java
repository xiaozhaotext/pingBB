package com.macro.mall.portal.domain;

/** Member identity is checked against the authenticated session, never trusted from JSON. */
public record MembershipPurchaseRequest(Long memberId, String level, String payChannel,
                                        String payMode, String requestId, String wechatOpenId) {}
