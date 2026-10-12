package br.com.brasil_saas.enterprise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

@Entity
@Table(name = "bc_int_webhook_event", schema = "brasil_saas")
@Getter
@Setter
public class IntWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id")
    private Long empresaId;

    @Column(name = "endpoint_id")
    private Long endpointId;

    @Column(name = "event_id", length = 160, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 160, nullable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "text", nullable = false)
    private String payload;

    @Column(name = "signature", length = 500)
    private String signature;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "RECEBIDO";

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    void prePersist() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
    }
}
