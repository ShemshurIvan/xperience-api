package com.xperience.service;

import com.xperience.dto.cart.CartItemRequest;
import com.xperience.dto.cart.CartItemResponse;
import com.xperience.entity.Product;
import com.xperience.entity.StatusSlot;
import com.xperience.entity.TimeSlot;
import com.xperience.entity.User;
import com.xperience.exception.BadRequestException;
import com.xperience.exception.conflict.SlotConflictException;
import com.xperience.exception.notfound.TimeSlotNotFoundException;
import com.xperience.exception.notfound.UserNotFoundException;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.ProductRepository;
import com.xperience.repository.TimeSlotRepository;
import com.xperience.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CartItemServiceTest {

    @Autowired
    private CartItemService cartItemService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    private User guest;
    private Product product;   // 200.00 per person
    private TimeSlot slot;     // 4 places, in 3 days

    @BeforeEach
    void setUp() {
        User host = saveUser("host@example.com");
        guest = saveUser("guest@example.com");

        product = new Product();
        product.setName("Drive a Lamborghini");
        product.setPrice(new BigDecimal("200.00"));
        product.setMaxPeople(4);
        product.setDurationMinutes(60);
        host.addProduct(product);
        productRepository.save(product);

        slot = saveSlot(LocalDateTime.now().plusDays(3), 4);
    }

    @Test
    void addToCart_createsLineWithPriceAndAvailability() {
        CartItemResponse response = cartItemService.addToCart(guest.getId(), new CartItemRequest(slot.getId(), 2));

        assertThat(response.id()).isNotNull();
        assertThat(response.timeSlotId()).isEqualTo(slot.getId());
        assertThat(response.productName()).isEqualTo("Drive a Lamborghini");
        assertThat(response.numberPeople()).isEqualTo(2);
        assertThat(response.lineTotal()).isEqualByComparingTo("400.00");
        assertThat(response.available()).isTrue();
        assertThat(response.addedAt()).isNotNull();
    }

    @Test
    void addToCart_updatesExistingLineInsteadOfAddingASecondOne() {
        CartItemResponse first = cartItemService.addToCart(guest.getId(), new CartItemRequest(slot.getId(), 1));

        CartItemResponse second = cartItemService.addToCart(guest.getId(), new CartItemRequest(slot.getId(), 3));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.numberPeople()).isEqualTo(3);
        assertThat(cartItemRepository.count()).isEqualTo(1);
    }

    @Test
    void addToCart_allowsTakingExactlyTheLastPlaces() {
        slot.setBookedPeople(2);

        CartItemResponse response = cartItemService.addToCart(guest.getId(), new CartItemRequest(slot.getId(), 2));

        assertThat(response.numberPeople()).isEqualTo(2);
    }

    @Test
    void addToCart_doesNotReservePlaces() {
        cartItemService.addToCart(guest.getId(), new CartItemRequest(slot.getId(), 2));

        assertThat(timeSlotRepository.findById(slot.getId()).orElseThrow().getBookedPeople()).isZero();
    }

    @Test
    void addToCart_throwsWhenNotEnoughPlacesLeft() {
        slot.setBookedPeople(3);

        assertThatThrownBy(() -> cartItemService.addToCart(guest.getId(), new CartItemRequest(slot.getId(), 2)))
                .isInstanceOf(SlotConflictException.class)
                .hasMessageContaining("Only 1 places left");
    }

    @Test
    void addToCart_throwsWhenSlotNotOpen() {
        slot.setStatus(StatusSlot.CANCELLED);

        assertThatThrownBy(() -> cartItemService.addToCart(guest.getId(), new CartItemRequest(slot.getId(), 1)))
                .isInstanceOf(SlotConflictException.class);
    }

    @Test
    void addToCart_throwsWhenSlotAlreadyStarted() {
        TimeSlot past = saveSlot(LocalDateTime.now().minusHours(1), 4);

        assertThatThrownBy(() -> cartItemService.addToCart(guest.getId(), new CartItemRequest(past.getId(), 1)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void addToCart_throwsWhenUserOrSlotMissing() {
        assertThatThrownBy(() -> cartItemService.addToCart(999_999L, new CartItemRequest(slot.getId(), 1)))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> cartItemService.addToCart(guest.getId(), new CartItemRequest(999_999L, 1)))
                .isInstanceOf(TimeSlotNotFoundException.class);
    }

    private User saveUser(String email) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAge(30);
        user.setEmail(email);
        user.setPassword("hashed-password");
        return userRepository.save(user);
    }

    private TimeSlot saveSlot(LocalDateTime start, int maxPeople) {
        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setStartTime(start);
        timeSlot.setEndTime(start.plusMinutes(product.getDurationMinutes()));
        timeSlot.setMaxPeopleInSlot(maxPeople);
        product.addTimeSlot(timeSlot);
        return timeSlotRepository.save(timeSlot);
    }
}