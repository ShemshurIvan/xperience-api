package com.xperience.service;

import com.xperience.dto.user.AccountType;
import com.xperience.dto.user.UserRequest;
import com.xperience.dto.user.UserResponse;
import com.xperience.entity.ReservationStatus;
import com.xperience.entity.Role;
import com.xperience.entity.User;
import com.xperience.exception.ActiveBookingsExistException;
import com.xperience.exception.EmailAlreadyExistsException;
import com.xperience.exception.ForbiddenException;
import com.xperience.exception.UserNotFoundException;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.ReservationRepository;
import com.xperience.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final CartItemRepository cartItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;

    public UserService(UserRepository userRepository,
                       ReservationRepository reservationRepository,
                       CartItemRepository cartItemRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin-email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.cartItemRepository = cartItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase();
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setAge(request.age());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(roleForNewUser(email, request.accountType()));

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException(email);
        }

        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateUser(Long currentUserId, UserRequest request) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException(currentUserId));

        String email = request.email().trim().toLowerCase();
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setAge(request.age());
        user.setPassword(passwordEncoder.encode(request.password()));

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> returnAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    /**
     * Deletes the user together with their experiences, time slots and cart.
     * Bookings the user made as a guest are kept, without the link to the user.
     * Refused while other guests have upcoming bookings on this user's experiences.
     */
    @Transactional
    public void deleteUserById(Long currentUserId, boolean isAdmin, Long id){
        if(!userRepository.existsById(id)){
            throw new UserNotFoundException(id);
        }
        if (!id.equals(currentUserId) && !isAdmin) {
            throw new ForbiddenException("You can only delete your own account");
        }

        boolean hasActiveBookings = reservationRepository.existsActiveBookingsOnOwnerSlots(
                id,
                List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED),
                LocalDateTime.now());
        if (hasActiveBookings) {
            throw new ActiveBookingsExistException(id);
        }

        reservationRepository.detachGuest(id);
        cartItemRepository.deleteByUserId(id);
        cartItemRepository.deleteByOwnerSlots(id);
        reservationRepository.deleteByOwnerSlots(id);

        userRepository.deleteById(id);
    }

    private Role roleForNewUser(String email, AccountType accountType) {
        if (!adminEmail.isEmpty() && adminEmail.equals(email)) {
            return Role.ADMIN;
        }
        return accountType == AccountType.SELLER ? Role.SELLER : Role.USER;
    }
}
