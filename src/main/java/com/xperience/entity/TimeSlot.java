package com.xperience.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "time_slots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_time_slot_product_start",
                columnNames = {"product_id", "start_time"}
        )
)
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "start_time", nullable = false)
    @NotNull
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    @NotNull
    private LocalDateTime endTime;

    @Column(name = "version")
    @Version
    private Long version;

    @Column(name = "max_people_in_slot")
    @NotNull
    @Min(1)
    private Integer maxPeopleInSlot;

    @Column(name = "booked_people")
    @NotNull
    @Min(0)
    private Integer bookedPeople = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @NotNull
    private StatusSlot status = StatusSlot.OPEN;

    public Integer getPlacesLeft(){
        return maxPeopleInSlot - bookedPeople;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Integer getBookedPeople() {
        return bookedPeople;
    }

    public void setBookedPeople(Integer bookedPeople) {
        this.bookedPeople = bookedPeople;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getMaxPeopleInSlot() {
        return maxPeopleInSlot;
    }

    public Long getVersion() {
        return version;
    }

    public void setMaxPeopleInSlot(Integer maxPeopleInSlot) {
        this.maxPeopleInSlot = maxPeopleInSlot;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public StatusSlot getStatus() {
        return status;
    }

    public void setStatus(StatusSlot status) {
        this.status = status;
    }

}
