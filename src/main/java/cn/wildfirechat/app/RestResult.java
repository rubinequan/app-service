package cn.wildfirechat.app;

public class RestResult {
    public enum  RestCode {
        SUCCESS(0, "success"),
        ERROR_INVALID_MOBILE(1, "无效的电话号码"),
        ERROR_PARAMS(2,"参数格式不正确"),
        ERROR_SEND_SMS_OVER_FREQUENCY(3, "请求验证码太频繁"),
        ERROR_SERVER_ERROR(4, "服务器异常"),
        ERROR_CODE_EXPIRED(5, "验证码已过期"),
        ERROR_CODE_INCORRECT(6, "验证码或密码错误"),
        ERROR_SERVER_CONFIG_ERROR(7, "服务器配置错误"),
        ERROR_SESSION_EXPIRED(8, "会话不存在或已过期"),
        ERROR_SESSION_NOT_VERIFIED(9, "会话没有验证"),
        ERROR_SESSION_NOT_SCANED(10, "会话没有被扫码"),
        ERROR_SERVER_NOT_IMPLEMENT(11, "功能没有实现"),
        ERROR_GROUP_ANNOUNCEMENT_NOT_EXIST(12, "群公告不存在"),
        ERROR_NOT_LOGIN(13, "没有登录"),
        ERROR_NO_RIGHT(14, "没有权限"),
        ERROR_INVALID_PARAMETER(15, "无效参数"),
        ERROR_NOT_EXIST(16, "对象不存在"),
        ERROR_USER_NAME_ALREADY_EXIST(17, "用户名已经存在"),
        ERROR_SESSION_CANCELED(18, "会话已经取消"),
        ERROR_PASSWORD_INCORRECT(19, "密码错误"),
        ERROR_FAILURE_TOO_MUCH_TIMES(20, "密码错误次数太多，请等5分钟再试试"),
        ERROR_USER_FORBIDDEN(21, "用户被封禁"),
        ERROR_PASSWORD_NULL(22, "密码不能为空"),
        ERROR_CODE(23, "验证码错误"),
        USER_EXISTS(24, "该手机号已注册"),
        USER_NOT_EXISTS(25, "手机号未注册，请先注册"),
        CODE_NOT_EXISTS(26, "验证码不存在"),
        CODE_ERROR(26, "验证码错误"),
        CODE_LIMIT(27, "验证码已达当天次数限制"),
        LOGIN_FAIL(28, "登录失败"),
        LOGIN_ERROR(28, "账号密码错误"),
        LOGIN_NO(29, "请先登录"),
        ADD_FAIL(30, "添加失败"),
        USER_NO_EXISTS(31, "举报人或者被举报人不存在"),
        JUBAOREN_NO_EXISTS(32, "举报人不存在"),
        BEIJUBAO_NO_EXISTS(33, "被举报人不存在"),
        DEL_FAIL(32, "删除失败"),
        BLACKLIST_FAIL(33, "注销失败"),
        FILE_ILLEGALTY(34, "图片违规，无法上传"),
        VIEDO_ILLEGALTY(34, "视频违规，无法上传"),
        REGISTER_FAIL(35, "注册失败"),
        SAVE_LOG_FAIL(36, "保存日志失败"),
        SEARCH_MESSAGE_FAIL(37, "查询消息异常"),
        NICKNAME_VALIDATE_FAIL(38, "昵称违规"),
        PORTRAIT_VALIDATE_FAIL(39, "头像违规"),
        ;
        public int code;
        public String msg;

        RestCode(int code, String msg) {
            this.code = code;
            this.msg = msg;
        }

    }
    private int code;
    private String message;
    private Object result;

    public static RestResult ok() {
        return new RestResult(RestCode.SUCCESS, null);
    }

    public static RestResult ok(Object object) {
        return new RestResult(RestCode.SUCCESS, object);
    }

    public static RestResult error(RestCode code) {
        return new RestResult(code, null);
    }

    public static RestResult result(RestCode code, Object object){
        return new RestResult(code, object);
    }

    public static RestResult result(int code, String message, Object object){
        RestResult r = new RestResult(RestCode.SUCCESS, object);
        r.code = code;
        r.message = message;
        return r;
    }

    private RestResult(RestCode code, Object result) {
        this.code = code.code;
        this.message = code.msg;
        this.result = result;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
