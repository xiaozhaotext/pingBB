package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.WechatWebOauthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/member/wechat-oauth")
public class WechatWebOauthController {
    private final UmsMemberService members;
    private final WechatWebOauthService oauth;

    public WechatWebOauthController(UmsMemberService members, WechatWebOauthService oauth) {
        this.members = members;
        this.oauth = oauth;
    }

    @GetMapping("/status")
    public CommonResult<Map<String, Boolean>> status(@RequestParam String purpose) {
        return CommonResult.success(Map.of("authorized", oauth.authorized(members.getCurrentMember().getId(), purpose)));
    }

    @PostMapping("/authorize")
    public CommonResult<Map<String, String>> authorize(@RequestParam String purpose,
                                                        @RequestParam(required = false) Long orderId) {
        return CommonResult.success(Map.of("url", oauth.authorize(members.getCurrentMember().getId(), purpose, orderId)));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(oauth.callback(code, state))).build();
    }
}
