package com.example.instagramclone;

import com.example.instagramclone.core.config.DotenvInitializer;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@EnableAsync
@SpringBootApplication
public class InstagramCloneTemplateApplication {

    public static void main(String[] args) {
        
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        boolean osEnvSet = System.getenv("SPRING_PROFILES_ACTIVE") != null;
        boolean jvmArgSet = System.getProperty("spring.profiles.active") != null;

        if (!osEnvSet && !jvmArgSet) {
            String profile = dotenv.get("SPRING_PROFILES_ACTIVE", "local");
            System.setProperty("spring.profiles.active", profile);
        }

        SpringApplication app = new SpringApplication(InstagramCloneTemplateApplication.class);
        app.addInitializers(new DotenvInitializer());
        app.run(args);
    }

}
