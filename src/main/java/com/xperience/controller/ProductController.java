package com.xperience.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.xperience.dto.product.ProductRequest;
import com.xperience.dto.product.ProductResponse;
import com.xperience.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Experiences", description = "Experiences that sellers offer")
@RestController
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Create an experience",
               description = "Sellers and admins. You become its owner.")
    @PostMapping("/createProduct")
    public ResponseEntity<ProductResponse> createProduct(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestBody @Valid ProductRequest productRequest) {
        ProductResponse response = productService.createProduct(Long.valueOf(jwt.getSubject()), productRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update your experience",
               description = "Only the owner can change it.")
    @PutMapping("/updateProduct/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable Long id,
                                                         @RequestBody @Valid ProductRequest productRequest) {
        ProductResponse response = productService.updateProduct(Long.valueOf(jwt.getSubject()), id, productRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get one experience")
    @SecurityRequirements
    @GetMapping("/getProduct/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @Operation(summary = "List experiences",
               description = "Paged. No account needed.")
    @SecurityRequirements
    @GetMapping("/products")
    public PagedModel<ProductResponse> findAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(productService.findAll(pageable));
    }

    @Operation(summary = "Delete an experience",
               description = "The owner, or an admin. Refused while it has upcoming bookings.")
    @DeleteMapping("/deleteProduct/{id}")
    public ResponseEntity<Void> deleteProduct(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
                                              @PathVariable Long id) {
        productService.deleteProductById(Long.valueOf(jwt.getSubject()), isAdmin(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
