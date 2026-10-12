package com.xperience.service;

import com.xperience.dto.product.ProductRequest;
import com.xperience.dto.product.ProductResponse;
import com.xperience.entity.Product;
import com.xperience.entity.ReservationStatus;
import com.xperience.entity.User;
import com.xperience.exception.ActiveBookingOnProductExistException;
import com.xperience.exception.ProductNotFoundException;
import com.xperience.exception.UserNotFoundException;
import com.xperience.repository.CartItemRepository;
import com.xperience.repository.ProductRepository;
import com.xperience.repository.ReservationRepository;
import com.xperience.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final CartItemRepository cartItemRepository;

    public ProductService(ProductRepository productRepository, UserRepository userRepository, ReservationRepository reservationRepository, CartItemRepository cartItemRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Transactional
    public ProductResponse createProduct(Long ownerId, ProductRequest productRequest) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new UserNotFoundException(ownerId));

        Product product = new Product();
        product.setName(productRequest.name().trim());
        product.setDescription(productRequest.description());
        product.setPrice(productRequest.price());
        product.setMaxPeople(productRequest.maxPeople());
        product.setDurationMinutes(productRequest.durationMinutes());
        owner.addProduct(product);

        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id,ProductRequest productRequest) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.setName(productRequest.name().trim());
        product.setDescription(productRequest.description());
        product.setPrice(productRequest.price());
        product.setMaxPeople(productRequest.maxPeople());
        product.setDurationMinutes(productRequest.durationMinutes());
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    @Transactional
    public void deleteProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        boolean hasActiveBooking = reservationRepository.existsActiveBookingsOnProduct(id,
                List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED),
                LocalDateTime.now());
        if (hasActiveBooking) {
            throw new ActiveBookingOnProductExistException(id);
        }

        cartItemRepository.deleteByProductSlots(id);
        reservationRepository.deleteByProductSlots(id);
        productRepository.deleteById(id);
    }
}
