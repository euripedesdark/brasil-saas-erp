package br.com.brasil_saas.ia.model;

import br.com.brasil_saas.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bc_ia_chat_message", schema = "brasil_saas")
@Getter @Setter
public class ChatMessage extends BaseEntity {
    
    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;
    
    @Column(columnDefinition = "text", nullable = false)
    private String content;
    
    @Column(name = "message_type", length = 20, nullable = false)
    private String messageType; // USER, ASSISTANT, SYSTEM
    
    @Column(name = "sender", length = 100)
    private String sender;
    
    @Column(name = "token_count")
    private Long tokenCount = 0L;
    
    @Column(name = "response_time_ms")
    private Long responseTimeMs;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
