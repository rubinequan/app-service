package cn.wildfirechat.app.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /**
     * 默认1注册日志
     * @return
     */
    int type() default  1;

    /**
     * 手机号
     * 不知道为啥，不能动态的从参数中获取手机号
     *      比如 #request.phone
     * @return
     */
    String phone() default "";
}
