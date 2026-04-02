package top.fusb.deploybot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final String workspaceRoot;

    public WebConfig(
            AuthInterceptor authInterceptor,
            @org.springframework.beans.factory.annotation.Value("${deploybot.workspace-root:./runtime}") String workspaceRoot
    ) {
        this.authInterceptor = authInterceptor;
        this.workspaceRoot = workspaceRoot;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("*")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String filesRoot = Path.of(workspaceRoot == null || workspaceRoot.isBlank() ? "./runtime" : workspaceRoot.trim())
                .toAbsolutePath()
                .normalize()
                .resolve("files")
                .toUri()
                .toString();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(filesRoot);
    }
}
