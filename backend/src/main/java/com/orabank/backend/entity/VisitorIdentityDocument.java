package com.orabank.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "visitor_identity_documents")
@Getter
@Setter
@NoArgsConstructor
public class VisitorIdentityDocument extends AuditableEntity {

    @Id
    @Column(name = "visitor_id")
    private Long visitorId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private byte[] content;

    public VisitorIdentityDocument(Long visitorId) {
        this.visitorId = visitorId;
    }
}
