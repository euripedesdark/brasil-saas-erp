package br.com.brasil_saas.enterprise.service;

import br.com.brasil_saas.financeiro.model.IntercompanyLancamento;
import br.com.brasil_saas.financeiro.repository.IntercompanyLancamentoRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.*;

class IntercompanyServiceTest {
    private final IntercompanyLancamentoRepository repository = mock(IntercompanyLancamentoRepository.class);
    private final IntercompanyService service = new IntercompanyService(repository);

    private static IntercompanyLancamento lancamento(Long empresa, BigDecimal valor, String moeda) {
        IntercompanyLancamento l = new IntercompanyLancamento();
        l.setEmpresaId(empresa);
        l.setValor(valor);
        l.setMoeda(moeda);
        l.setNumero("IC-1");
        l.setReconciliado(true);
        l.setCompetencia(LocalDate.of(2026, 10, 1));
        return l;
    }

    @Test
    void consultaMesInteiroIsolandoEmpresaEMoedas() {
        when(repository.findByEmpresaIdAndReconciliadoTrueAndDeletedAtIsNullAndCompetenciaGreaterThanEqualAndCompetenciaLessThanOrderByNumero(
                eq(7L), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 11, 1))))
                .thenReturn(List.of(
                        lancamento(7L, new BigDecimal("100.00"), "BRL"),
                        lancamento(7L, new BigDecimal("20.00"), "USD"),
                        lancamento(7L, new BigDecimal("50.00"), "BRL")));
        Map<String, Object> r = service.eliminacoes(7L, LocalDate.of(2026, 10, 15));
        assertEquals(Map.of("BRL", new BigDecimal("150.00"), "USD", new BigDecimal("20.00")), r.get("totaisPorMoeda"));
        assertNull(r.get("total"));
        assertEquals("2026-10-01", r.get("competencia"));
        verify(repository).findByEmpresaIdAndReconciliadoTrueAndDeletedAtIsNullAndCompetenciaGreaterThanEqualAndCompetenciaLessThanOrderByNumero(
                eq(7L), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void preservaTotalParaMoedaUnica() {
        when(repository.findByEmpresaIdAndReconciliadoTrueAndDeletedAtIsNullAndCompetenciaGreaterThanEqualAndCompetenciaLessThanOrderByNumero(
                anyLong(), any(), any())).thenReturn(List.of(
                        lancamento(7L, new BigDecimal("5.00"), "USD")));
        assertEquals(new BigDecimal("5.00"), service.eliminacoes(7L, LocalDate.of(2026, 10, 1)).get("total"));
    }

    @Test
    void semLinhasRetornaZero() {
        when(repository.findByEmpresaIdAndReconciliadoTrueAndDeletedAtIsNullAndCompetenciaGreaterThanEqualAndCompetenciaLessThanOrderByNumero(
                anyLong(), any(), any())).thenReturn(List.of());
        Map<String, Object> r = service.eliminacoes(7L, LocalDate.of(2026, 12, 31));
        assertEquals(BigDecimal.ZERO, r.get("total"));
        assertEquals(Map.of(), r.get("totaisPorMoeda"));
        verify(repository).findByEmpresaIdAndReconciliadoTrueAndDeletedAtIsNullAndCompetenciaGreaterThanEqualAndCompetenciaLessThanOrderByNumero(
                eq(7L), eq(LocalDate.of(2026, 12, 1)), eq(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void reconciliacaoExigeCompetenciaIgualNoEspelho() {
        LocalDate competencia = LocalDate.of(2026, 10, 1);
        IntercompanyLancamento meu = new IntercompanyLancamento();
        meu.setId(99L);
        meu.setEmpresaId(7L);
        meu.setEmpresaParceiraId(8L);
        meu.setNumero("IC-99");
        meu.setValor(new BigDecimal("100"));
        meu.setMoeda("BRL");
        meu.setCompetencia(competencia);
        when(repository.findByIdAndEmpresaIdAndDeletedAtIsNull(eq(99L), eq(7L)))
                .thenReturn(Optional.of(meu));
        when(repository.findEspelho(eq(8L), eq(7L), eq("IC-99"), eq(competencia)))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("SEM_CONTRAPARTIDA", service.reconciliar(7L, 10L, 99L).get("status"));
        verify(repository).findEspelho(eq(8L), eq(7L), eq("IC-99"), eq(competencia));
        ArgumentCaptor<IntercompanyLancamento> captor = ArgumentCaptor.forClass(IntercompanyLancamento.class);
        verify(repository).save(captor.capture());
        assertEquals("SEM_CONTRAPARTIDA", captor.getValue().getStatus());
        assertEquals(false, captor.getValue().getReconciliado());
        assertNull(captor.getValue().getContrapartidaId());
    }
}
