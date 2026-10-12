package br.com.brasil_saas.enterprise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

@Entity
@Table(name = "bc_tms_documento_entrega", schema = "brasil_saas")
@Getter
@Setter
public class TmsDocumentoEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "ordem_id", nullable = false)
    private Long ordemId;

    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo = "COMPROVANTE_ENTREGA";

    @Column(name = "numero", length = 120)
    private String numero;

    @Column(name = "recebedor", length = 160)
    private String recebedor;

    @Column(name = "recebido_em")
    private LocalDateTime recebidoEm;

    @Column(name = "assinatura_localizacao", length = 500)
    private String assinaturaLocalizacao;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    @Column(name = "documento_localizacao", length = 1000)
    private String documentoLocalizacao;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "PENDENTE";
}
