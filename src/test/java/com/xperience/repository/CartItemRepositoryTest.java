package com.xperience.repository;

import com.xperience.entity.CartItem;
import com.xperience.entity.Product;
import com.xperience.entity.TimeSlot;
import com.xperience.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CartItemRepositoryTest {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private EntityManager entityManager;

    private User guest;
    private User otherGuest;
    private TimeSlot lamborghiniSlot;
    private TimeSlot balloonSlot;

    @BeforeEach
    void setUp() {
        User host = saveUser("host@example.com");
        guest = saveUser("guest@example.com");
        otherGuest = saveUser("other@example.com");

        lamborghiniSlot = saveSlot(saveProduct(host, "Drive a Lamborghini"), LocalDateTime.now().plusDays(3));
        balloonSlot = saveSlot(saveProduct(host, "Hot air balloon"), LocalDateTime.now().plusDays(4));

        saveCartItem(guest, lamborghiniSlot, 2);
        saveCartItem(guest, balloonSlot, 1);
        saveCartItem(otherGuest, lamborghiniSlot, 3);

        // start the tests with an empty persistence context, as a real request would
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findByUserId_returnsOnlyThatUsersCart() {
        List<CartItem> cart = cartItemRepository.findByUserId(guest.getId());

        assertThat(cart)
                .extracting(item -> item.getTimeSlot().getProduct().getName())
                .containsExactly("Drive a Lamborghini", "Hot air balloon");
    }

    @Test
    void findByUserId_loadsSlotsAndProductsInOneQuery() {
        Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<CartItem> cart = cartItemRepository.findByUserId(guest.getId());
        cart.forEach(item -> item.getTimeSlot().getProduct().getName());   // would trigger lazy loading without join fetch

        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        stats.setStatisticsEnabled(false);
    }

    @Test
    void findByUserIdAndTimeSlotId_findsExistingLine() {
        assertThat(cartItemRepository.findByUserIdAndTimeSlotId(guest.getId(), lamborghiniSlot.getId()))
                .hasValueSatisfying(item -> assertThat(item.getNumberPeople()).isEqualTo(2));
    }

    @Test
    void findByUserIdAndTimeSlotId_isEmptyWhenSlotNotInThatUsersCart() {
        assertThat(cartItemRepository.findByUserIdAndTimeSlotId(otherGuest.getId(), balloonSlot.getId()))
                .isEmpty();
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

    private Product saveProduct(User owner, String name) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal("200.00"));
        product.setMaxPeople(4);
        product.setDurationMinutes(60);
        owner.addProduct(product);
        return productRepository.save(product);
    }

    private TimeSlot saveSlot(Product product, LocalDateTime start) {
        TimeSlot slot = new TimeSlot();
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(product.getDurationMinutes()));
        slot.setMaxPeopleInSlot(4);
        product.addTimeSlot(slot);
        return timeSlotRepository.save(slot);
    }

    private void saveCartItem(User user, TimeSlot slot, int people) {
        CartItem item = new CartItem();
        item.setUser(user);
        item.setTimeSlot(slot);
        item.setNumberPeople(people);
        cartItemRepository.save(item);
    }
}