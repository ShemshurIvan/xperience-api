package com.xperience.repository;

import com.xperience.entity.TimeSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    Page<TimeSlot> findByProductIdAndStartTimeBetweenOrderByStartTime(Long productId,
                                                                      LocalDateTime from,
                                                                      LocalDateTime to,
                                                                      Pageable pageable);

    @Query("""
            select count(s) > 0 from TimeSlot s
            where s.product.id = :productId
              and s.status <> com.xperience.entity.StatusSlot.CANCELLED
              and s.startTime < :newEnd
              and s.endTime > :newStart
            """)
    boolean existsOverlappingSlot(@Param("productId") Long productId,
                                  @Param("newStart") LocalDateTime newStart,
                                  @Param("newEnd") LocalDateTime newEnd);


    @Query("""
          select count(s) > 0 from TimeSlot s
          where s.product.id = :productId
            and s.id <> :excludeId
            and s.status <> com.xperience.entity.StatusSlot.CANCELLED
            and s.startTime < :newEnd
            and s.endTime > :newStart
          """)
    boolean existsOverlappingSlotExcluding(@Param("productId") Long productId,
                                           @Param("excludeId") Long excludeId,
                                           @Param("newStart") LocalDateTime newStart,
                                           @Param("newEnd") LocalDateTime newEnd);

}