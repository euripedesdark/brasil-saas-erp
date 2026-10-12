package br.com.brasil_saas.contabilidade.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.brasil_saas.contabilidade.model.CtbLancamento;
import br.com.brasil_saas.contabilidade.model.CtbPartida;
import br.com.brasil_saas.contabilidade.model.CtbPendencia;
import br.com.brasil_saas.contabilidade.model.RegraLancamento;
import br.com.brasil_saas.contabilidade.repository.CtbLancamentoRepository;
import br.com.brasil_saas.contabilidade.repository.CtbPendenciaRepository;
import br.com.brasil_saas.contabilidade.repository.RegraLancamentoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EventoContabilServiceTest {

    @Mock ContabilidadeService contabilidade;
    @Mock CtbLancamentoRepository lancamentos;
    @Mock RegraLancamentoRepository regras;
    @Mock CtbPendenciaRepository pendencias;
    EventoContabilService svc;

    @BeforeEach
    void setUp() { svc = new EventoContabilService(contabilidade, lancamentos, regras, pendencias); }

    private RegraLancamento regra(Long debito, Long credito) {
        RegraLancamento r = new RegraLancamento();
        r.setContaDebitoId(debito);
        r.setContaCreditoId(credito);
        r.setCentroCustoId(0L);
        r.setHistorico("Baixa auto");
        return r;
    }

    @Test
    void geraLancamentoPartidaDobradaQuandoHaRegra() {
        when(lancamentos.findByEmpresaIdAndOrigemTipoAndOrigemIdAndStatusInAndDeletedAtIsNull(
                anyLong(), anyString(), anyLong(), anyCollection())).thenReturn(List.of());
        when(regras.findFirstByEmpresaIdAndCodigoAndAtivoTrue(eq(1L), eq(EventoContabilService.EVT_BAIXA_TITULO)))
                .thenReturn(Optional.of(regra(10L, 20L)));
        when(pendencias.findByEmpresaIdAndOrigemTipoAndOrigemIdAndEventoAndStatus(
                anyLong(), anyString(), anyLong(), anyString(), eq("ABERTA"))).thenReturn(Optional.empty());
        when(contabilidade.salvar(eq(1L), any(CtbLancamento.class))).thenAnswer(inv -> {
            CtbLancamento l = inv.getArgument(1); l.setId(100L); return l;
        });
        when(contabilidade.addPartida(eq(1L), eq(100L), any(CtbPartida.class))).thenAnswer(inv -> inv.getArgument(2));
        CtbLancamento lancado = new CtbLancamento(); lancado.setId(100L); lancado.setStatus("LANCADO");
        when(contabilidade.lancar(1L, 100L)).thenReturn(lancado);
        when(contabilidade.totais(1L, 100L)).thenReturn(new BigDecimal[]{new BigDecimal("50.00"), new BigDecimal("50.00")});

        var r = svc.contabilizar(1L, EventoContabilService.EVT_BAIXA_TITULO, "BAIXA", 7L,
                LocalDate.of(2026, 10, 10), new BigDecimal("50.00"), "teste");
        assertTrue(r.lancamento().isPresent());
        assertFalse(r.pendente());
        ArgumentCaptor<CtbPartida> partidas = ArgumentCaptor.forClass(CtbPartida.class);
        verify(contabilidade, times(2)).addPartida(eq(1L), eq(100L), partidas.capture());
        BigDecimal deb = partidas.getAllValues().stream().map(CtbPartida::getDebito).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cre = partidas.getAllValues().stream().map(CtbPartida::getCredito).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, deb.compareTo(cre));
        assertEquals(0, deb.compareTo(new BigDecimal("50.00")));
    }

    @Test
    void repeticaoNaoDuplica() {
        CtbLancamento existente = new CtbLancamento(); existente.setId(5L); existente.setStatus("LANCADO");
        when(lancamentos.findByEmpresaIdAndOrigemTipoAndOrigemIdAndStatusInAndDeletedAtIsNull(
                anyLong(), anyString(), anyLong(), anyCollection())).thenReturn(List.of(existente));
        var r = svc.contabilizar(1L, EventoContabilService.EVT_BAIXA_TITULO, "BAIXA", 7L,
                LocalDate.now(), new BigDecimal("10"), null);
        assertEquals("JA_EXISTIA", r.motivo());
        verify(contabilidade, never()).salvar(anyLong(), any());
    }

    @Test
    void semRegraGeraPendenciaSemFalhar() {
        when(lancamentos.findByEmpresaIdAndOrigemTipoAndOrigemIdAndStatusInAndDeletedAtIsNull(
                anyLong(), anyString(), anyLong(), anyCollection())).thenReturn(List.of());
        when(regras.findFirstByEmpresaIdAndCodigoAndAtivoTrue(eq(1L), eq(EventoContabilService.EVT_FATURAMENTO_VENDA)))
                .thenReturn(Optional.empty());
        when(pendencias.findByEmpresaIdAndOrigemTipoAndOrigemIdAndEvento(
                anyLong(), anyString(), anyLong(), anyString())).thenReturn(Optional.empty());
        when(pendencias.save(any(CtbPendencia.class))).thenAnswer(inv -> inv.getArgument(0));
        var r = svc.contabilizar(1L, EventoContabilService.EVT_FATURAMENTO_VENDA, "PEDIDO", 9L,
                LocalDate.now(), new BigDecimal("100"), null);
        assertTrue(r.pendente());
        assertTrue(r.lancamento().isEmpty());
        ArgumentCaptor<CtbPendencia> captor = ArgumentCaptor.forClass(CtbPendencia.class);
        verify(pendencias).save(captor.capture());
        CtbPendencia p = captor.getValue();
        assertEquals(1L, p.getEmpresaId());
        assertEquals(EventoContabilService.EVT_FATURAMENTO_VENDA, p.getEvento());
        assertEquals("PEDIDO", p.getOrigemTipo());
        assertEquals(9L, p.getOrigemId());
        assertEquals("ABERTA", p.getStatus());
    }

    @Test
    void periodoFechadoPropagaExcecao() {
        when(lancamentos.findByEmpresaIdAndOrigemTipoAndOrigemIdAndStatusInAndDeletedAtIsNull(
                anyLong(), anyString(), anyLong(), anyCollection())).thenReturn(List.of());
        when(regras.findFirstByEmpresaIdAndCodigoAndAtivoTrue(eq(1L), eq(EventoContabilService.EVT_BAIXA_TITULO)))
                .thenReturn(Optional.of(regra(1L, 2L)));
        when(contabilidade.salvar(eq(1L), any())).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Periodo fechado"));
        when(pendencias.findByEmpresaIdAndOrigemTipoAndOrigemIdAndEvento(
                anyLong(), anyString(), anyLong(), anyString())).thenReturn(Optional.empty());
        when(pendencias.save(any(CtbPendencia.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThrows(ResponseStatusException.class, () ->
                svc.contabilizar(1L, EventoContabilService.EVT_BAIXA_TITULO, "BAIXA", 1L,
                        LocalDate.now(), new BigDecimal("10"), null));
    }
}
