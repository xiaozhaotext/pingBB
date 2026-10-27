package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.WechatWebOauthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
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
    public ResponseEntity<CommonResult<Map<String, String>>> authorize(@RequestParam String purpose,
                                                        @RequestParam(required = false) Long orderId) {
        var authorization = oauth.authorize(members.getCurrentMember().getId(), purpose, orderId);
        var cookie = ResponseCookie.from("mall_wechat_oauth_state", authorization.state())
                .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(300).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(CommonResult.success(Map.of("url", authorization.url())));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state,
                                         @CookieValue(value = "mall_wechat_oauth_state", required = false) String browserState) {
        var clear = ResponseCookie.from("mall_wechat_oauth_state", "")
                .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(0).build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, clear.toString())
                .location(URI.create(oauth.callback(code, state, browserState))).build();
    }
}
