package com.xperience.config;

import com.xperience.entity.Role;
import com.xperience.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Admins can't be created through the API. On startup, the account with ADMIN_EMAIL (if it exists) becomes ADMIN.
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final String adminEmail;

    public AdminAccountInitializer(UserRepository userRepository,
                                   @Value("${app.admin-email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isEmpty()) {
            return;
        }
        userRepository.findByEmail(adminEmail).ifPresent(user -> {
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                log.warn("User {} was promoted to ADMIN", user.getId());
            }
        });
    }
}