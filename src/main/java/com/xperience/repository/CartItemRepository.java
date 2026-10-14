package com.xperience.repository;

import com.xperience.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // The user's whole cart, with each slot and its product loaded in the same query (no N+1)
    @Query("""
            select c from CartItem c
            join fetch c.timeSlot s
            join fetch s.product
            where c.user.id = :userId
            order by c.addedAt
            """)
    List<CartItem> findByUserId(@Param("userId") Long userId);

    // Is this slot already in the user's cart?
    Optional<CartItem> findByUserIdAndTimeSlotId(Long userId, Long timeSlotId);

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CartItem c where c.timeSlot.id = :timeSlotId")
    int deleteByTimeSlotId(@Param("timeSlotId") Long timeSlotId);

}