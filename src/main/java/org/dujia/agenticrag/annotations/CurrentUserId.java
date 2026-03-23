package org.dujia.agenticrag.annotations;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
    // 如果拿不到 userId 就报错
    boolean required() default true;
}
