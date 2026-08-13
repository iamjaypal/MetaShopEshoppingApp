package ShoppingCart.demo.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/product_img/**")
                .addResourceLocations("file:/C:/Users/Owner/OneDrive/Desktop/JavaQuestions/SpringBoot_Project/demo/demo/product_img/");

        registry.addResourceHandler("/profile_img/**")
                .addResourceLocations("file:/C:/Users/Owner/OneDrive/Desktop/JavaQuestions/SpringBoot_Project/demo/demo/profile_img/");
    }
}
