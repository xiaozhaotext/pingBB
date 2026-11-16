package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.InviteRegistrationService;
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
import org.springframework.web.bind.annotation.RequestBody;
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
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private InviteRegistrationService inviteRegistrationService;
    @Autowired
    private com.macro.mall.portal.service.SmsVerificationService verification;

    @Operation(summary = "会员注册")
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult register(@RequestParam String username,
                                 @RequestParam String password,
                                 @RequestParam String telephone,
                                 @RequestParam String authCode,
                                 @RequestParam(required = false) String inviteCode) {
        inviteRegistrationService.register(username, password, telephone, authCode, inviteCode);
        return CommonResult.success(null,"注册成功");
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
        UmsMember member = memberService.getById(memberService.getCurrentMember().getId());
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

    public record NicknameUpdate(String nickname) {}

    @Operation(summary = "修改当前会员昵称")
    @RequestMapping(value = "/profile/nickname", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Void> updateNickname(@RequestBody NicknameUpdate request) {
        String nickname = request == null || request.nickname() == null ? "" : request.nickname().trim();
        if (nickname.length() < 2 || nickname.length() > 24 || nickname.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "昵称须为2至24个字符，不能包含控制字符");
        }
        memberService.updateNickname(memberService.getCurrentMember().getId(), nickname);
        return CommonResult.success(null);
    }

    @Operation(summary = "获取验证码")
    @RequestMapping(value = "/getAuthCode", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public CommonResult getAuthCode(@RequestParam String telephone, HttpServletRequest request) {
        try {
            Map<String, Object> result = verification.send(telephone, request);
            return CommonResult.success(result, Boolean.TRUE.equals(result.get("sandboxTest")) ? "沙箱测试请求已受理" : "验证码已发送");
        } catch (ResponseStatusException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码暂时无法发送，请稍后重试");
        }
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<CommonResult<Void>> verificationError(ResponseStatusException error) {
        CommonResult<Void> result = CommonResult.failed(error.getReason());
        result.setCode(error.getStatusCode().value());
        return org.springframework.http.ResponseEntity.status(error.getStatusCode()).body(result);
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
