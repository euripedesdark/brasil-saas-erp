package br.com.brasil_saas.core.service;

import br.com.brasil_saas.core.model.RelatorioResponse;
import br.com.brasil_saas.financeiro.repository.LancamentoPartidaRepository;
import br.com.brasil_saas.financeiro.repository.ProjecaoFluxoCaixaRepository;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import br.com.brasil_saas.fiscal.repository.NfeRepository;
import br.com.brasil_saas.producao.repository.ProducaoRepository;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Relatórios gerenciais.
 *
 * Todas as consultas são JPQL com tenant (empresa_id) sempre parametrizado —
 * nunca interpolado — para evitar injecao e vazamento entre empresas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelatorioService {

    private static final Pageable LIMITE = PageRequest.of(0, 1000);

    private final PedidoVendaRepository pedidoVendaRepository;
    private final TituloRepository tituloRepository;
    private final ProducaoRepository producaoRepository;
    private final NfeRepository nfeRepository;
    private final LancamentoPartidaRepository partidaRepository;
    private final ProjecaoFluxoCaixaRepository projecaoRepository;

    public RelatorioResponse gerarRelatorio(String tipo, Long empresaId, Map<String, Object> filtros) {
        log.info("Gerando relatório {} para empresa {}", tipo, empresaId);

        String tipoNormalizado = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
        RelatorioResponse response = new RelatorioResponse();
        List<Map<String, Object>> dados;
        Map<String, Object> resumo = new LinkedHashMap<>();
        String periodo;

        LocalDate inicio = data(filtros, "dataInicio");
        LocalDate fim = data(filtros, "dataFim");
        periodo = (inicio == null ? "início" : inicio.toString())
                + " a " + (fim == null ? "hoje" : fim.toString());

        switch (tipoNormalizado) {
            case "VENDAS" -> {
                dados = relatorioVendas(empresaId, inicio, fim);
                resumo.put("totalVendas", total(dados, "valor_total"));
                resumo.put("quantidadePedidos", total(dados, "quantidade_pedidos"));
                response.setNomeRelatorio("Relatório de Vendas");
            }
            case "FINANCEIRO" -> {
                dados = relatorioFinanceiro(empresaId, inicio, fim);
                resumo.put("totalReceitas", total(dados, "valor_original", "R"));
                resumo.put("totalDespesas", total(dados, "valor_original", "P"));
                response.setNomeRelatorio("Fluxo de Caixa / Financeiro");
            }
            case "PRODUCAO" -> {
                dados = relatorioProducao(empresaId, inicio, fim);
                resumo.put("totalProduzido", total(dados, "quantidade_planejada"));
                resumo.put("custoTotal", total(dados, "custo_total"));
                response.setNomeRelatorio("Rendimento de Produção");
            }
            case "FISCAL" -> {
                dados = relatorioFiscal(empresaId, inicio, fim);
                resumo.put("totalNotas", total(dados, "quantidade_notas"));
                resumo.put("valorTotalNotas", total(dados, "valor_total"));
                response.setNomeRelatorio("Resumo de Notas Fiscais");
            }
            case "DRE" -> {
                dados = relatorioDre(empresaId, inicio, fim);
                resumo.put("totalDebitos", total(dados, "debitos"));
                resumo.put("totalCreditos", total(dados, "creditos"));
                resumo.put("resultado", total(dados, "resultado"));
                response.setNomeRelatorio("DRE Gerencial");
            }
            case "FLUXO_CAIXA" -> {
                dados = relatorioFluxoCaixa(empresaId, inicio, fim);
                resumo.put("totalEntradas", total(dados, "valor_previsto_entrada"));
                resumo.put("totalSaidas", total(dados, "valor_previsto_saida"));
                resumo.put("saldoProjetado", total(dados, "saldo_projetado"));
                response.setNomeRelatorio("Fluxo de Caixa Projetado");
            }
            default -> throw new IllegalArgumentException("Tipo de relatório não suportado: " + tipo);
        }

        response.setDados(dados);
        response.setResumo(resumo);
        response.setPeriodo(periodo);
        return response;
    }

    private List<Map<String, Object>> relatorioVendas(Long empresaId, LocalDate inicio, LocalDate fim) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : pedidoVendaRepository.relatorioVendas(empresaId, inicio, fim, LIMITE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", r.getNumero());
            m.put("data_emissao", r.getDataEmissao());
            m.put("cliente", r.getCliente());
            m.put("valor_produtos", r.getValorProdutos());
            m.put("valor_desconto", r.getValorDesconto());
            m.put("valor_total", r.getValorTotal());
            m.put("status", r.getStatus());
            m.put("quantidade_pedidos", r.getQuantidadePedidos());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> relatorioFinanceiro(Long empresaId, LocalDate inicio, LocalDate fim) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : tituloRepository.relatorioFinanceiro(empresaId, inicio, fim, LIMITE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero_documento", r.getNumeroDocumento());
            m.put("data_emissao", r.getDataEmissao());
            m.put("data_vencimento", r.getDataVencimento());
            m.put("descricao", r.getDescricao());
            m.put("tipo", r.getTipo());
            m.put("valor_original", r.getValorOriginal());
            m.put("valor_saldo", r.getValorSaldo());
            m.put("status", r.getStatus());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> relatorioProducao(Long empresaId, LocalDate inicio, LocalDate fim) {
        java.time.LocalDateTime de = inicio == null ? null : inicio.atStartOfDay();
        java.time.LocalDateTime ate = fim == null ? null : fim.atTime(LocalTime.MAX);
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : producaoRepository.relatorioProducao(empresaId, de, ate, LIMITE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", r.getNumero());
            m.put("data_inicio", r.getDataInicio());
            m.put("data_fim", r.getDataFim());
            m.put("produto_final", r.getProdutoFinal());
            m.put("quantidade_planejada", r.getQuantidadePlanejada());
            m.put("custo_total", r.getCustoTotal());
            m.put("status", r.getStatus());
            m.put("tipo_producao", r.getTipoProducao());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> relatorioDre(Long empresaId, LocalDate inicio, LocalDate fim) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : partidaRepository.relatorioDre(empresaId, inicio, fim, LIMITE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo", r.getCodigo());
            m.put("descricao", r.getDescricao());
            m.put("natureza", r.getNatureza());
            m.put("debitos", r.getDebitos());
            m.put("creditos", r.getCreditos());
            m.put("resultado", r.getResultado());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> relatorioFluxoCaixa(Long empresaId, LocalDate inicio, LocalDate fim) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : projecaoRepository.relatorioFluxoCaixa(empresaId, inicio, fim, LIMITE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("data_projecao", r.getDataProjecao());
            m.put("valor_previsto_entrada", r.getValorPrevistoEntrada());
            m.put("valor_previsto_saida", r.getValorPrevistoSaida());
            m.put("saldo_projetado", r.getSaldoProjetado());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> relatorioFiscal(Long empresaId, LocalDate inicio, LocalDate fim) {
        java.time.LocalDateTime de = inicio == null ? null : inicio.atStartOfDay();
        java.time.LocalDateTime ate = fim == null ? null : fim.atTime(LocalTime.MAX);
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : nfeRepository.relatorioFiscal(empresaId, de, ate, LIMITE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", r.getNumero());
            m.put("serie", r.getSerie());
            m.put("natureza_operacao", r.getNaturezaOperacao());
            m.put("data_emissao", r.getDataEmissao());
            m.put("status", r.getStatus());
            m.put("valor_total", r.getValorTotal());
            m.put("quantidade_notas", r.getQuantidadeNotas());
            out.add(m);
        }
        return out;
    }

    private LocalDate data(Map<String, Object> filtros, String chave) {
        Object valor = filtros.get(chave);
        if (valor == null) return null;
        try {
            return LocalDate.parse(String.valueOf(valor));
        } catch (DateTimeParseException e) {
            log.warn("Data inválida em {}: {}", chave, valor);
            return null;
        }
    }

    private Double total(List<Map<String, Object>> dados, String campo) {
        return total(dados, campo, null);
    }

    private Double total(List<Map<String, Object>> dados, String campo, String tipoTitulo) {
        double soma = 0;
        for (Map<String, Object> linha : dados) {
            if (tipoTitulo != null) {
                Object tipo = linha.get("tipo");
                if (tipo == null || !tipoTitulo.equals(String.valueOf(tipo))) continue;
            }
            soma += numero(linha.get(campo));
        }
        return soma;
    }

    private double numero(Object valor) {
        if (valor instanceof BigDecimal bd) return bd.doubleValue();
        if (valor instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
