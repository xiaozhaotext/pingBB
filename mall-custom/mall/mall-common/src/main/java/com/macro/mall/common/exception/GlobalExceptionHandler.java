package com.macro.mall.common.exception;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.macro.mall.common.api.CommonResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLSyntaxErrorException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理类
 * Created by macro on 2020/2/27.
 *
 * 约定：凡是「请求数据」有问题，都要告诉前端到底哪里有问题——
 * 多传了什么、少了什么、哪个字段类型或格式不对，避免只返回一句 400 参数错误。
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseBody
    @ExceptionHandler(value = ApiException.class)
    public CommonResult handle(ApiException e) {
        if (e.getErrorCode() != null) {
            return CommonResult.failed(e.getErrorCode());
        }
        return CommonResult.failed(e.getMessage());
    }

    @ResponseBody
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public CommonResult handleValidException(MethodArgumentNotValidException e) {
        return CommonResult.validateFailed(fieldMessage(e.getBindingResult()));
    }

    @ResponseBody
    @ExceptionHandler(value = BindException.class)
    public CommonResult handleValidException(BindException e) {
        return CommonResult.validateFailed(fieldMessage(e.getBindingResult()));
    }

    /** @RequestParam / @PathVariable 上的校验不通过时（Spring 6.1+）。 */
    @ResponseBody
    @ExceptionHandler(value = HandlerMethodValidationException.class)
    public CommonResult handleMethodValidation(HandlerMethodValidationException e) {
        List<String> problems = new ArrayList<>();
        List<? extends MessageSourceResolvable> errors = e.getAllErrors();
        if (errors != null) {
            for (MessageSourceResolvable error : errors) {
                String text = readable(safe(error.getDefaultMessage()));
                if (!text.isEmpty()) problems.add(text);
            }
        }
        return CommonResult.validateFailed(assemble("提交的数据不符合要求", problems, null, null, null, null, ""));
    }

    @ResponseBody
    @ExceptionHandler(value = ConstraintViolationException.class)
    public CommonResult handleConstraint(ConstraintViolationException e) {
        List<String> problems = new ArrayList<>();
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        if (violations != null) {
            for (ConstraintViolation<?> violation : violations) {
                problems.add("「" + violation.getPropertyPath() + "」" + readable(safe(violation.getMessage())));
            }
        }
        return CommonResult.validateFailed(assemble("提交的数据不符合要求", problems, null, null, null, null, ""));
    }

    /** 请求体不是合法 JSON、字段类型或格式对不上、或后端收到了不认识的字段。 */
    @ResponseBody
    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public CommonResult handleNotReadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        LOGGER.warn("请求数据解析失败: {}", cause == null ? e.getMessage() : cause.getMessage());
        String where = location(e.getCause());
        List<String> extra = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> wrongType = new ArrayList<>();
        List<String> syntax = new ArrayList<>();

        if (cause instanceof UnrecognizedPropertyException unknown) {
            String owner = unknown.getReferringClass() == null ? "" : "（" + unknown.getReferringClass().getSimpleName() + " 里）";
            extra.add("字段「" + unknown.getPropertyName() + "」" + owner + "后端不认识，属于多传的数据，请删除或改正字段名");
        } else if (cause instanceof InvalidFormatException invalid) {
            String path = pathOf(invalid);
            wrongType.add("字段「" + (path.isEmpty() ? "未知字段" : path) + "」的值「" + safe(String.valueOf(invalid.getValue()))
                    + "」格式不对，应为 " + typeName(invalid.getTargetType()));
        } else if (cause instanceof MismatchedInputException mismatched) {
            String path = pathOf(mismatched);
            String raw = safe(mismatched.getOriginalMessage());
            if (raw.toLowerCase().contains("missing required creator property")) {
                missing.add("缺少必填字段「" + (path.isEmpty() ? raw : path) + "」");
            } else if (path.isEmpty()) {
                syntax.add(raw.isEmpty() ? "请求数据的结构不对" : raw);
            } else {
                wrongType.add("字段「" + path + "」的数据类型不对，应为 " + typeName(mismatched.getTargetType()));
            }
        } else if (cause instanceof JsonProcessingException json) {
            syntax.add(jsonProblem(safe(json.getOriginalMessage())));
        } else {
            syntax.add(safe(cause == null ? e.getMessage() : cause.getMessage()));
        }
        return CommonResult.validateFailed(assemble("提交的请求数据有问题", null, extra, missing, wrongType, syntax, where));
    }

    /** 少了 URL 上的必填参数。 */
    @ResponseBody
    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public CommonResult handleMissingParameter(MissingServletRequestParameterException e) {
        return CommonResult.validateFailed("缺少必填参数「" + e.getParameterName() + "」（类型 " + e.getParameterType()
                + "），请在请求里补上这个参数");
    }

    /** 少了 multipart 里的文件/表单部分。 */
    @ResponseBody
    @ExceptionHandler(value = MissingServletRequestPartException.class)
    public CommonResult handleMissingPart(MissingServletRequestPartException e) {
        return CommonResult.validateFailed("缺少上传内容「" + e.getRequestPartName() + "」，请重新选择文件后再提交");
    }

    @ResponseBody
    @ExceptionHandler(value = MissingPathVariableException.class)
    public CommonResult handleMissingPathVariable(MissingPathVariableException e) {
        return CommonResult.validateFailed("请求地址里缺少路径参数「" + e.getVariableName() + "」");
    }

    /** 参数类型不对，例如 id 传了文字。 */
    @ResponseBody
    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    public CommonResult handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expected = e.getRequiredType() == null ? "正确类型" : typeName(e.getRequiredType());
        return CommonResult.validateFailed("参数「" + e.getName() + "」应为 " + expected
                + "，实际传入的是「" + safe(String.valueOf(e.getValue())) + "」");
    }

    @ResponseBody
    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    public CommonResult handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        Set<String> supported = new LinkedHashSet<>();
        if (e.getSupportedHttpMethods() != null) {
            e.getSupportedHttpMethods().forEach(method -> supported.add(method.name()));
        }
        return CommonResult.validateFailed("接口不支持 " + e.getMethod() + " 请求"
                + (supported.isEmpty() ? "" : "，请改用 " + String.join("/", supported)));
    }

    @ResponseBody
    @ExceptionHandler(value = HttpMediaTypeNotSupportedException.class)
    public CommonResult handleMediaType(HttpMediaTypeNotSupportedException e) {
        return CommonResult.validateFailed("请求的 Content-Type 不支持：" + safe(String.valueOf(e.getContentType())));
    }

    @ResponseBody
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public CommonResult handleTooLarge(MaxUploadSizeExceededException e) {
        long max = e.getMaxUploadSize();
        return CommonResult.validateFailed(max > 0
                ? "上传的文件太大，单次最多 " + (max / 1024 / 1024) + "MB，请压缩或拆分后再传"
                : "上传的文件太大，请压缩或拆分后再传");
    }

    /** 业务代码主动抛出的状态异常：保留状态码，同时把原因原样带回前端。 */
    @ResponseBody
    @ExceptionHandler(value = ResponseStatusException.class)
    public ResponseEntity<CommonResult<Void>> handleResponseStatus(ResponseStatusException e) {
        String reason = safe(e.getReason());
        LOGGER.warn("请求被拒绝 status={} reason={}", e.getStatusCode(), reason);
        String message = reason.isEmpty() ? "请求被拒绝（" + e.getStatusCode().value() + "）" : reason;
        return ResponseEntity.status(e.getStatusCode()).body(CommonResult.failed(message));
    }

    @ResponseBody
    @ExceptionHandler(value = SQLSyntaxErrorException.class)
    public CommonResult handleSQLSyntaxErrorException(SQLSyntaxErrorException e) {
        return CommonResult.failed("数据处理失败，请联系管理员");
    }

    @ResponseBody
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public CommonResult handleDatabaseException(org.springframework.dao.DataAccessException e) {
        LOGGER.error("Database operation failed", e);
        return CommonResult.failed("数据处理失败，请联系管理员");
    }

    // ------------------------------------------------------------------ helpers

    /** 校验类异常：把所有不合规字段都列出来，而不是只报第一个。 */
    private static String fieldMessage(BindingResult bindingResult) {
        if (bindingResult == null || !bindingResult.hasErrors()) return "提交的数据不符合要求";
        List<String> missing = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            String field = error.getField();
            String detail = readable(safe(error.getDefaultMessage()));
            if (error.isBindingFailure()) {
                wrong.add("字段「" + field + "」的值「" + safe(String.valueOf(error.getRejectedValue()))
                        + "」格式不对" + (detail.isEmpty() ? "" : "：" + detail));
            } else if (detail.contains("不能为空") || detail.contains("must not be")) {
                missing.add("「" + field + "」" + detail);
            } else {
                wrong.add("「" + field + "」" + detail);
            }
        }
        for (ObjectError error : bindingResult.getGlobalErrors()) {
            wrong.add(safe(error.getDefaultMessage()));
        }
        return assemble("提交的数据不符合要求", null, null, missing, wrong, null, "");
    }

    private static String assemble(String title, List<String> problems, List<String> extra, List<String> missing,
                                   List<String> wrongType, List<String> syntax, String where) {
        StringBuilder sb = new StringBuilder(title);
        if (where != null && !where.isEmpty()) sb.append("（位置：").append(where).append("）");
        appendBlock(sb, "多出来的数据：", extra);
        appendBlock(sb, "缺少的数据：", missing);
        appendBlock(sb, "类型或格式不对：", wrongType);
        appendBlock(sb, "内容本身读不出来：", syntax);
        appendBlock(sb, "问题明细：", problems);
        return sb.toString();
    }

    private static void appendBlock(StringBuilder sb, String label, List<String> items) {
        if (items == null || items.isEmpty()) return;
        List<String> cleaned = items.stream().map(GlobalExceptionHandler::safe)
                .filter(text -> !text.isEmpty()).collect(Collectors.toList());
        if (cleaned.isEmpty()) return;
        sb.append("\n").append(label);
        for (String item : cleaned) sb.append("\n· ").append(item);
    }

    /** 把 Jackson 的字段路径拼成 a.b[0].c 这种可读形式。 */
    private static String pathOf(JsonMappingException mapping) {
        List<JsonMappingException.Reference> path = mapping.getPath();
        if (path == null || path.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference reference : path) {
            if (reference.getFieldName() != null) {
                if (sb.length() > 0) sb.append('.');
                sb.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                sb.append('[').append(reference.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    /** JSON 解析出错的位置，便于定位第几行第几列。 */
    private static String location(Throwable cause) {
        JsonProcessingException json = null;
        if (cause instanceof JsonProcessingException direct) {
            json = direct;
        } else if (cause != null && cause.getCause() instanceof JsonProcessingException nested) {
            json = nested;
        }
        if (json == null) return "";
        JsonLocation location = json.getLocation();
        if (location == null || location.getLineNr() <= 0) return "";
        return "第 " + location.getLineNr() + " 行第 " + location.getColumnNr() + " 列";
    }

    /** 把 Jackson 的英文报错翻成能看懂的说明。 */
    private static String jsonProblem(String original) {
        String text = original == null ? "" : original;
        if (text.contains("Unexpected end-of-input")) return "JSON 没写完：括号或引号没有闭合（少了 } 或 \"）";
        if (text.contains("Unexpected character")) return "JSON 里有非法字符，请检查引号、逗号是否用错";
        if (text.contains("Duplicate field")) return "同一个字段重复出现，请只保留一个";
        if (text.contains("Unexpected token")) return "JSON 结构不对，多了或少了大括号/中括号";
        if (text.contains("Cannot deserialize value of type")) return "字段的数据类型对不上";
        return "请求数据不是合法的 JSON，请检查格式";
    }

    /** 把 Bean Validation 的英文默认提示翻成中文。 */
    private static String readable(String detail) {
        if (detail == null || detail.isEmpty()) return "";
        return detail
                .replace("must not be empty", "不能为空")
                .replace("must not be blank", "不能为空")
                .replace("must not be null", "不能为空")
                .replace("must be a well-formed email address", "不是合法的邮箱地址")
                .replace("size must be between", "长度必须在")
                .replace("must be greater than or equal to", "不能小于")
                .replace("must be less than or equal to", "不能大于")
                .replace("must be greater than", "必须大于")
                .replace("must be less than", "必须小于")
                .replace("must match", "格式不符合要求")
                .replace("must be true", "必须为 true")
                .replace("must be false", "必须为 false")
                .replace("numeric value out of bounds", "数值超出允许范围")
                .replace("must be a number", "必须是数字")
                .replace("must not be", "不能为");
    }

    private static String typeName(Class<?> type) {        if (type == null) return "数字或文本";
        if (type == String.class) return "文本";
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class
                || type == Short.class || type == short.class) return "整数";
        if (type == java.math.BigDecimal.class || type == Double.class || type == double.class
                || type == Float.class || type == float.class) return "数字";
        if (type == Boolean.class || type == boolean.class) return "true/false";
        if (java.util.Date.class.isAssignableFrom(type)) return "时间（如 2026-11-07 10:00:00）";
        if (type.isArray() || java.util.Collection.class.isAssignableFrom(type)) return "列表";
        if (type.isEnum()) return "枚举 " + java.util.Arrays.toString(type.getEnumConstants());
        return type.getSimpleName();
    }

    private static String safe(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "…" : trimmed;
    }
}
