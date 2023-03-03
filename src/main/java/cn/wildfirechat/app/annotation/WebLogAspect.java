package cn.wildfirechat.app.annotation;

import cn.wildfirechat.app.RestResult;
import cn.wildfirechat.app.Service;
import cn.wildfirechat.pojos.InputLog;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
@Component
@Order(1)
public class WebLogAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebLogAspect.class);

    @Autowired
    private Service mService;

    @Pointcut("@annotation(cn.wildfirechat.app.annotation.Log)")
    public void webLog() {
    }

    @Around("webLog()")
    public Object doAround(ProceedingJoinPoint joinPoint) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        Object[] args = joinPoint.getArgs();
        // 因#request.phone不能获取手机号，从参数中获取
        String phone = "";
        for (Object arg : args) {
            if (ObjectUtils.isEmpty(arg)) {
                continue;
            }
            JSONObject jsonObject = JSON.parseObject(JSON.toJSONString(arg));
            phone = jsonObject.getString("phone");
            if (!StringUtils.isEmpty(phone)) {
                break;
            }
        }
        Log log = getLog(joinPoint);
        InputLog logPojo = new InputLog();
        logPojo.setIp(getIp(request));
        logPojo.setServerIp(request.getRemoteUser());
        logPojo.setPort(request.getServerPort());
        logPojo.setType(log.type());
        logPojo.setMac(request.getHeader("mac"));
        logPojo.setModel(request.getHeader("model"));
        logPojo.setPhone(phone);
        logPojo.setMessageId("-1");
        // 执行方法
        Object proceed = null;
        try {
            proceed = joinPoint.proceed();
            // 如果注册成功，保存日志信息
            if (proceed instanceof RestResult) {
                RestResult restResult = (RestResult) proceed;
                logPojo.setFlag(restResult.getCode() == 0 ? true : false);
                logPojo.setRemark(restResult.getMessage());
            }
        } catch (Throwable throwable) {
            logPojo.setFlag(false);
            logPojo.setRemark(throwable.getLocalizedMessage());
            throwable.printStackTrace();
        } finally {
            // 保存日志
            logPojo.setMessageId("");
            mService.saveLog(logPojo);;
        }
        return proceed;
    }

    private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (!StringUtils.isEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.isEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个IP值，第一个为真实IP。
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index);
            } else {
                return ip;
            }
        } else {
            return request.getRemoteAddr();
        }
    }

    private static Log getLog(JoinPoint point) {
        MethodSignature methodSignature = (MethodSignature) point.getSignature();
        if (null != methodSignature) {
            Method method = methodSignature.getMethod();
            if (null != method) {
                return method.getAnnotation(Log.class);
            }
        }
        return null;
    }
}