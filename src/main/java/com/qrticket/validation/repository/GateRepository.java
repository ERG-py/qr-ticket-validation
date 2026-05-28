package com.qrticket.validation.repository;

import com.qrticket.validation.entity.Gate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GateRepository extends JpaRepository<Gate, Long> {
}