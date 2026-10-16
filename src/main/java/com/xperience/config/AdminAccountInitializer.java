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

/**
 * Promotes the account whose email matches {@code app.admin-email} (the {@code ADMIN_EMAIL}
 * environment variable) to {@link Role#ADMIN} when the application starts.
 *
 * <p>Admin accounts can't be created through the API: registration only accepts
 * {@code USER} or {@code SELLER}. This runner covers accounts that already existed before
 * the admin email was set; new registrations with that email become admins in
 * {@link com.xperience.service.UserService}.
 *
 * <p>Does nothing if no admin email is configured or no account uses it.
 */
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