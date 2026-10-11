package br.com.brasil_saas.plm.service;

import br.com.brasil_saas.plm.model.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/** Ciclo de vida de engenharia: mudanca com aprovacao e efeitos, e revisoes de produto. */
public interface PlmService {

    List<PlmMudanca> mudancas(Long empresaId);

    PlmMudanca criarMudanca(Long empresaId, Long userId, MudancaRequest request);

    PlmMudanca enviarAprovacao(Long empresaId, Long userId, Long mudancaId, Long aprovadorId);

    PlmMudanca decidirEtapa(Long empresaId, Long userId, Long mudancaId, boolean aprovar, String observacao);

    PlmMudanca implementar(Long empresaId, Long userId, Long mudancaId);

    List<PlmEfeito> efeitos(Long empresaId, Long mudancaId);

    PlmEfeito adicionarEfeito(Long empresaId, EfeitoRequest request);

    List<PlmRevisao> revisoes(Long empresaId, Long produtoId);

    PlmRevisao criarRevisao(Long empresaId, Long userId, RevisaoRequest request);

    PlmRevisao vigorarRevisao(Long empresaId, Long userId, Long revisaoId);

    record MudancaRequest(@NotBlank String numero, String tipo, @NotBlank String titulo,
                          String descricao, String prioridade) {
    }

    record EfeitoRequest(@NotNull @Positive Long mudancaId, @NotBlank String entidadeTipo,
                         @NotNull @Positive Long entidadeId, @NotBlank String acao,
                         String revisaoAnterior, String revisaoNova, LocalDate efetivaEm,
                         Integer ordemExecucao, String observacao) {
    }

    record RevisaoRequest(@NotNull @Positive Long produtoId, @NotBlank String revisao,
                          String descricao, String motivo) {
    }
}
