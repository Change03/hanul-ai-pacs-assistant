package com.hanul.aipacs.config;

import com.hanul.aipacs.domain.UserEntity;
import com.hanul.aipacs.domain.enums.Role;
import com.hanul.aipacs.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DemoDataInitializer {
    @Bean
    CommandLineRunner seedDemoUser(UserRepository users, PasswordEncoder encoder) {
        return args -> users.findByUsername("demo").orElseGet(() -> {
            UserEntity user = new UserEntity();
            user.setUsername("demo");
            user.setPasswordHash(encoder.encode("demo"));
            user.setRole(Role.RADIOLOGIST_DEMO);
            return users.save(user);
        });
    }
}
