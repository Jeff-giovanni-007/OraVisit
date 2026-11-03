package com.orabank.backend.repository;

import com.orabank.backend.entity.Visit;
import com.orabank.backend.entity.VisitStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitRepository extends JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {

    @Override
    @EntityGraph(attributePaths = {"visitor", "hostEmployee", "registeredBy"})
    Page<Visit> findAll(Specification<Visit> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"visitor", "hostEmployee", "registeredBy"})
    List<Visit> findAll(Specification<Visit> specification, Sort sort);

    @EntityGraph(attributePaths = {"visitor", "hostEmployee", "registeredBy"})
    Optional<Visit> findWithDetailsById(Long id);

    boolean existsByBadgeNumberIgnoreCaseAndStatus(String badgeNumber, VisitStatus status);

    boolean existsByBadgeNumberIgnoreCaseAndStatusAndIdNot(String badgeNumber, VisitStatus status, Long id);

    boolean existsByVisitorIdAndStatus(Long visitorId, VisitStatus status);

    boolean existsByIdAndRegisteredById(Long id, Long registeredById);

    boolean existsByVisitorIdAndRegisteredByIdNot(Long visitorId, Long registeredById);

    long countByHostEmployeeIdOrRegisteredById(Long hostEmployeeId, Long registeredById);

    @Modifying
    @Query("DELETE FROM Visit v WHERE v.visitor.id = :visitorId")
    int deleteByVisitorId(@Param("visitorId") Long visitorId);

    long countByStatus(VisitStatus status);

    long countByCheckInAtGreaterThanEqualAndCheckInAtLessThan(Instant from, Instant to);

    long countByStatusAndCheckInAtGreaterThanEqualAndCheckInAtLessThan(
            VisitStatus status, Instant from, Instant to);

    long countByRegisteredByIdAndStatus(Long registeredById, VisitStatus status);

    long countByRegisteredByIdAndCheckInAtGreaterThanEqualAndCheckInAtLessThan(
            Long registeredById, Instant from, Instant to);

    long countByRegisteredByIdAndStatusAndCheckInAtGreaterThanEqualAndCheckInAtLessThan(
            Long registeredById, VisitStatus status, Instant from, Instant to);

    @Query("""
            SELECT v.visitor.id AS visitorId, COUNT(v) AS total
            FROM Visit v
            WHERE v.visitor.id IN :visitorIds
            GROUP BY v.visitor.id
            """)
    List<VisitorCount> countByVisitorIds(@Param("visitorIds") Collection<Long> visitorIds);

    @Query("""
            SELECT v.visitor.id AS visitorId, COUNT(v) AS total
            FROM Visit v
            WHERE v.visitor.id IN :visitorIds AND v.registeredBy.id = :agentId
            GROUP BY v.visitor.id
            """)
    List<VisitorCount> countByVisitorIdsAndRegisteredById(@Param("visitorIds") Collection<Long> visitorIds,
                                                          @Param("agentId") Long agentId);

    interface VisitorCount {
        Long getVisitorId();

        long getTotal();
    }
}
