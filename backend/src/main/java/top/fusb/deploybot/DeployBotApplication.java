package top.fusb.deploybot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class DeployBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeployBotApplication.class, args);
    }
}
