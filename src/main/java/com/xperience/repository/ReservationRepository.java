package com.xperience.repository;

import com.xperience.entity.Reservation;
import com.xperience.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {


    @Query("""
            select count(r) > 0 from Reservation r
            where r.timeSlot.product.owner.id = :ownerId
              and (r.guest is null or r.guest.id <> :ownerId)
              and r.status in :statuses
              and r.timeSlot.startTime > :now
            """)
    boolean existsActiveBookingsOnOwnerSlots(@Param("ownerId") Long ownerId,
                                             @Param("statuses") Collection<ReservationStatus> statuses,
                                             @Param("now") LocalDateTime now);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reservation r set r.guest = null where r.guest.id = :guestId")
    int detachGuest(@Param("guestId") Long guestId);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from Reservation r
            where r.timeSlot.id in (select s.id from TimeSlot s where s.product.owner.id = :ownerId)
            """)
    int deleteByOwnerSlots(@Param("ownerId") Long ownerId);


    @Query("""
          select count(r) > 0 from Reservation r
          where r.timeSlot.product.id = :productId
            and r.status in :statuses
            and r.timeSlot.startTime > :now
          """)
    boolean existsActiveBookingsOnProduct(@Param("productId") Long productId,
                                          @Param("statuses") Collection<ReservationStatus> statuses,
                                          @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
          delete from Reservation r
          where r.timeSlot.id in (select s.id from TimeSlot s where s.product.id = :productId)
          """)
    int deleteByProductSlots(@Param("productId") Long productId);

    boolean existsByTimeSlotId(Long timeSlotId);

    // Cancels every booking on the slot that isn't cancelled yet
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
          update Reservation r
          set r.status = com.xperience.entity.ReservationStatus.CANCELLED
          where r.timeSlot.id = :timeSlotId
            and r.status <> com.xperience.entity.ReservationStatus.CANCELLED
          """)
    int cancelActiveByTimeSlotId(@Param("timeSlotId") Long timeSlotId);


}