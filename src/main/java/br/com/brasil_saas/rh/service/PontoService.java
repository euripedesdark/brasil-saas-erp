package br.com.brasil_saas.rh.service;
import br.com.brasil_saas.rh.model.Funcionario;
import br.com.brasil_saas.rh.model.Ponto;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import br.com.brasil_saas.rh.repository.PontoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class PontoService {
    private final PontoRepository repo;
    private final FuncionarioRepository funcionarios;
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    private void exigirAtivo(Long empresaId, Long funcionarioId) {
        Funcionario f = exigir(funcionarios.findById(funcionarioId), "Funcionario inexistente");
        if (f.getEmpresaId() == null || !f.getEmpresaId().equals(empresaId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionario de outra empresa");
        if (!Boolean.TRUE.equals(f.getAtivo())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Funcionario inativo");
    }
    public List<Ponto> espelho(Long empresaId, Long funcionarioId, Integer ano, Integer mes) {
        exigirAtivo(empresaId, funcionarioId);
        List<Ponto> base = repo.findByEmpresaIdAndFuncionarioIdAndDeletedAtIsNullOrderByDataDesc(empresaId, funcionarioId);
        if (ano == null || mes == null) return base;
        return base.stream().filter(p -> p.getData() != null && p.getData().getYear() == ano && p.getData().getMonthValue() == mes).toList();
    }
    @Transactional public Ponto bater(Long empresaId, Long funcionarioId) {
        exigirAtivo(empresaId, funcionarioId);
        LocalDate hoje = LocalDate.now();
        Ponto p = repo.findByEmpresaIdAndFuncionarioIdAndDataAndDeletedAtIsNull(empresaId, funcionarioId, hoje).orElseGet(() -> {
            Ponto n = new Ponto();
            n.setFuncionarioId(funcionarioId);
            n.setData(hoje);
            return n; });
        LocalTime agora = LocalTime.now().withSecond(0).withNano(0);
        if (p.getE1() == null) p.setE1(agora);
        else if (p.getS1() == null) p.setS1(agora);
        else if (p.getE2() == null) p.setE2(agora);
        else if (p.getS2() == null) p.setS2(agora);
        else throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Dia ja fechado");
        p.setHorasTrabalhadas(horas(p));
        p.setFalta(false);
        return repo.save(p);
    }
    @Transactional public Ponto ajustar(Long empresaId, Long id, LocalTime e1, LocalTime s1, LocalTime e2, LocalTime s2, String observacao) {
        Ponto p = exigir(repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Registro inexistente");
        p.setE1(e1);
        p.setS1(s1);
        p.setE2(e2);
        p.setS2(s2);
        p.setObservacao(observacao);
        p.setHorasTrabalhadas(horas(p));
        return repo.save(p);
    }
    @Transactional public Ponto falta(Long empresaId, Long funcionarioId, LocalDate data, String observacao) {
        exigirAtivo(empresaId, funcionarioId);
        if (data == null) data = LocalDate.now();
        Optional<Ponto> atual = repo.findByEmpresaIdAndFuncionarioIdAndDataAndDeletedAtIsNull(empresaId, funcionarioId, data);
        Ponto p = atual.orElseGet(Ponto::new);
        p.setFuncionarioId(funcionarioId);
        p.setData(data);
        p.setFalta(true);
        p.setHorasTrabalhadas(BigDecimal.ZERO);
        p.setObservacao(observacao);
        return repo.save(p);
    }
    private BigDecimal horas(Ponto p) {
        long min = 0;
        if (p.getE1() != null && p.getS1() != null) min += Duration.between(p.getE1(), p.getS1()).toMinutes();
        if (p.getE2() != null && p.getS2() != null) min += Duration.between(p.getE2(), p.getS2()).toMinutes();
        if (min < 0) min = 0;
        return BigDecimal.valueOf(min).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
