package br.com.brasil_saas.ia.model;

import br.com.brasil_saas.shared.model.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bc_ia_chat_session", schema = "brasil_saas")
@Getter @Setter
public class ChatSession extends TenantEntity {
    
    @Column(length = 200, nullable = false)
    private String title;
    
    @Column(columnDefinition = "text")
    private String context;
    
    @Column(name = "model_name", length = 100)
    private String modelName = "gpt-4";
    
    @Column(name = "temperature")
    private Double temperature = 0.7;
    
    @Column(name = "max_tokens")
    private Integer maxTokens = 4000;
    
    @Column(name = "session_type", length = 50)
    private String sessionType = "GENERAL";
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "token_count")
    private Long tokenCount = 0L;
    
    @Column(name = "cost_usd")
    private Double costUsd = 0.0;
    
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ChatMessage> messages = new ArrayList<>();
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
