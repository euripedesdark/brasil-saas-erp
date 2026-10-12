package br.com.brasil_saas.cadastro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ServicoRequest(
    @NotBlank @Size(max = 30) String codigo,
    @NotBlank @Size(max = 200) String nome,
    String descricao,
    @Size(max = 10) String lc116Codigo,
    // Codigo municipal de Sao Paulo, 4 digitos. Ex.: 2919. Ver a migration V92.
    @Size(max = 10) String codigoTributacaoMunicipal,
    @Size(max = 6) String codigoTributacaoNacional,
    @Size(max = 12) String nbs,
    BigDecimal aliquotaIss,
    BigDecimal preco,
    Long unidadeMedidaId,
    Boolean ativo
) {}
