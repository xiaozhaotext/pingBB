package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.component.MessageRecorder;
import com.macro.mall.portal.service.SmsSendService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * 会员管理Controller
 * Created by macro on 2018/8/3.
 */
@Controller
@Tag(name = "UmsMemberController", description = "会员登录注册管理")
@RequestMapping("/sso")
public class UmsMemberController {
    @Value("${jwt.tokenHeader}")
    private String tokenHeader;
    @Value("${jwt.tokenHead}")
    private String tokenHead;
    @Value("${mall.payment.mock.enabled:false}")
    private boolean mockPaymentEnabled;
    @Value("${mall.sms.enabled:false}")
    private boolean smsEnabled;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private MessageRecorder messageRecorder;
    @Autowired
    private SmsSendService smsSendService;

    @Operation(summary = "会员注册")
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult register(@RequestParam String username,
                                 @RequestParam String password,
                                 @RequestParam String telephone,
                                 @RequestParam String authCode) {
        memberService.register(username, password, telephone, authCode);
        return CommonResult.success(null,"注册成功");
    }

    @Operation(summary = "沙箱测试账号注册能力")
    @RequestMapping(value = "/testRegistrationEnabled", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Boolean> testRegistrationEnabled() {
        return CommonResult.success(mockPaymentEnabled && !smsEnabled);
    }

    @Operation(summary = "会员登录")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult login(@RequestParam String username,
                              @RequestParam String password) {
        String token = memberService.login(username, password);
        if (token == null) {
            return CommonResult.validateFailed("用户名或密码错误");
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", token);
        tokenMap.put("tokenHead", tokenHead);
        return CommonResult.success(tokenMap);
    }

    @Operation(summary = "获取会员信息")
    @RequestMapping(value = "/info", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult info(Principal principal) {
        if(principal==null){
            return CommonResult.unauthorized(null);
        }
        UmsMember member = memberService.getCurrentMember();
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", member.getId());
        profile.put("username", member.getUsername());
        profile.put("nickname", member.getNickname());
        profile.put("phone", member.getPhone());
        profile.put("icon", member.getIcon());
        profile.put("gender", member.getGender());
        profile.put("birthday", member.getBirthday());
        profile.put("city", member.getCity());
        profile.put("memberLevelId", member.getMemberLevelId());
        profile.put("integration", member.getIntegration());
        profile.put("growth", member.getGrowth());
        profile.put("status", member.getStatus());
        return CommonResult.success(profile);
    }

    @Operation(summary = "获取验证码")
    @RequestMapping(value = "/getAuthCode", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult getAuthCode(@RequestParam String telephone) {
        String authCode = memberService.generateAuthCode(telephone);
        SmsSendService.SmsSendResult result = smsSendService.sendCode(telephone, authCode);
        messageRecorder.record("SMS", telephone, "登录/注册验证码", "AUTH_CODE", telephone,
                result.status(), result.errorMessage());
        if (!result.sent() && "FAILED".equals(result.status())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "短信发送失败，请稍后重试");
        }
        // 通道未启用时仅在接口返回（仅测试）；通道启用后默认不回显，避免生产泄露验证码。
        Object data = result.codeEchoAllowed() ? authCode : null;
        String message = result.sent() ? "验证码已发送" : "未配置短信通道，验证码仅在接口返回（仅测试）";
        return CommonResult.success(data, message);
    }

    @Operation(summary = "会员修改密码")
    @RequestMapping(value = "/updatePassword", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updatePassword(@RequestParam String telephone,
                                 @RequestParam String password,
                                 @RequestParam String authCode) {
        memberService.updatePassword(telephone,password,authCode);
        return CommonResult.success(null,"密码修改成功");
    }


    @Operation(summary = "刷新token")
    @RequestMapping(value = "/refreshToken", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult refreshToken(HttpServletRequest request) {
        String token = request.getHeader(tokenHeader);
        String refreshToken = memberService.refreshToken(token);
        if (refreshToken == null) {
            return CommonResult.failed("token已经过期！");
        }
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("token", refreshToken);
        tokenMap.put("tokenHead", tokenHead);
        return CommonResult.success(tokenMap);
    }
}
