package com.qrticket.validation.repository;

import com.qrticket.validation.entity.Checkin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckinRepository extends JpaRepository<Checkin, Long> {

    Optional<Checkin> findByTicketId(Long ticketId);
}