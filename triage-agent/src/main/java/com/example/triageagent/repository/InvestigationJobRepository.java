package com.example.triageagent.repository;

import com.example.triageagent.model.InvestigationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InvestigationJobRepository extends JpaRepository<InvestigationJob, UUID> {
}