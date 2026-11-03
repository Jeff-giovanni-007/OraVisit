package com.orabank.backend.repository;

import com.orabank.backend.entity.VisitorIdentityDocument;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitorIdentityDocumentRepository extends JpaRepository<VisitorIdentityDocument, Long> {

    @Query("SELECT d.visitorId FROM VisitorIdentityDocument d WHERE d.visitorId IN :visitorIds")
    List<Long> findVisitorIdsIn(@Param("visitorIds") Collection<Long> visitorIds);
}
