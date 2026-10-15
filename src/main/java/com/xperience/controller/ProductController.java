package com.xperience.controller;

import com.xperience.dto.product.ProductRequest;
import com.xperience.dto.product.ProductResponse;
import com.xperience.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // the logged-in user becomes the owner
    @PostMapping("/createProduct")
    public ResponseEntity<ProductResponse> createProduct(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestBody @Valid ProductRequest productRequest) {
        ProductResponse response = productService.createProduct(Long.valueOf(jwt.getSubject()), productRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/updateProduct/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable Long id,
                                                         @RequestBody @Valid ProductRequest productRequest) {
        ProductResponse response = productService.updateProduct(Long.valueOf(jwt.getSubject()), id, productRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/getProduct/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @GetMapping("/products")
    public PagedModel<ProductResponse> findAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(productService.findAll(pageable));
    }

    @DeleteMapping("/deleteProduct/{id}")
    public ResponseEntity<Void> deleteProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        productService.deleteProductById(Long.valueOf(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }
}