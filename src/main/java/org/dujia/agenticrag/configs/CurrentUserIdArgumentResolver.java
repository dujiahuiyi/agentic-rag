package org.dujia.agenticrag.configs;

import org.dujia.agenticrag.annotations.CurrentUserId;
import org.dujia.agenticrag.contexts.UserContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Configuration
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean hasParameterAnnotation = parameter.hasParameterAnnotation(CurrentUserId.class);
        boolean equals = parameter.getParameterType().equals(Long.class);
        return hasParameterAnnotation && equals;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        CurrentUserId annotation = parameter.getParameterAnnotation(CurrentUserId.class);
        Long userId = UserContext.get();
        if (userId == null && annotation != null && annotation.required()) {
            throw new RuntimeException("无法获取当前登录用户信息");
        }
        return userId;
    }
}
