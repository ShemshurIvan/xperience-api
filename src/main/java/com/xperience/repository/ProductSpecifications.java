package com.xperience.repository;

import com.xperience.entity.Product;
import com.xperience.entity.StatusSlot;
import com.xperience.entity.TimeSlot;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ProductSpecifications {
    private ProductSpecifications(){}
    public static Specification<Product> searchByName(String name) {
        return((root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
    }

    public static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        return((root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
    }

    public static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        return((root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
    }
    public static Specification<Product> fitsGroupOf(int people){
        return ((root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("maxPeople"), people));
    }

    public static Specification<Product> hasFreeSlotBetween(LocalDateTime from, LocalDateTime to, int people) {
        return (root, query, cb) -> {
            Subquery<Long> slot = query.subquery(Long.class);
            Root<TimeSlot> s = slot.from(TimeSlot.class);
            slot.select(s.get("id")).where(
                    cb.equal(s.get("product"), root),
                    cb.equal(s.get("status"), StatusSlot.OPEN),
                    cb.between(s.get("startTime"), from, to),
                    cb.greaterThanOrEqualTo(cb.diff(s.get("maxPeopleInSlot"), s.get("bookedPeople")), people));
            return cb.exists(slot);
        };
    }
}
