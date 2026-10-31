package com.gatekeeper.gateway.config;

import com.gatekeeper.gateway.interceptor.ApiKeyRateLimitInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GatewayWebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<ApiKeyRateLimitInterceptor> interceptorProvider;

    public GatewayWebMvcConfig(ObjectProvider<ApiKeyRateLimitInterceptor> interceptorProvider) {
        this.interceptorProvider = interceptorProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        ApiKeyRateLimitInterceptor interceptor = interceptorProvider.getIfAvailable();
        if (interceptor != null) {
            registry.addInterceptor(interceptor)
                    .addPathPatterns("/api/v1/protected/**");
        }
    }
}
