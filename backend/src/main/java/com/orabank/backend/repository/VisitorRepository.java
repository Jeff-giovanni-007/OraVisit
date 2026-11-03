package com.orabank.backend.repository;

import com.orabank.backend.entity.IdentityDocumentType;
import com.orabank.backend.entity.Visitor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitorRepository extends JpaRepository<Visitor, Long>, JpaSpecificationExecutor<Visitor> {

    Optional<Visitor> findByIdentityDocumentTypeAndIdentityCardNumberIgnoreCase(
            IdentityDocumentType type, String identityCardNumber);

    @Query("""
            SELECT COUNT(v) > 0
            FROM Visitor v
            WHERE v.id = :visitorId
              AND (v.createdBy.id = :agentId
                   OR EXISTS (SELECT 1 FROM Visit vi
                              WHERE vi.visitor = v AND vi.registeredBy.id = :agentId))
            """)
    boolean existsAccessibleBy(@Param("visitorId") Long visitorId, @Param("agentId") Long agentId);

    @Modifying
    @Query("UPDATE Visitor v SET v.createdBy = NULL WHERE v.createdBy.id = :employeeId")
    int clearCreatedBy(@Param("employeeId") Long employeeId);
}
