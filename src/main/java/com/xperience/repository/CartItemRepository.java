package com.xperience.repository;

import com.xperience.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CartItem c where c.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from CartItem c
            where c.timeSlot.id in (select s.id from TimeSlot s where s.product.owner.id = :ownerId)
            """)
    int deleteByOwnerSlots(@Param("ownerId") Long ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
          delete from CartItem c
          where c.timeSlot.id in (select s.id from TimeSlot s where s.product.id = :productId)
          """)
    int deleteByProductSlots(@Param("productId") Long productId);

}