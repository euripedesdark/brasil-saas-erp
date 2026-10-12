package br.com.brasil_saas.rh.service;

import br.com.brasil_saas.rh.model.Funcionario;
import br.com.brasil_saas.rh.model.Rescisao;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import br.com.brasil_saas.rh.repository.RescisaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RescisaoService {

    private final FuncionarioRepository funcionarios;
    private final RescisaoRepository rescisoes;

    @Transactional(readOnly = true)
    public Map<String, Object> calcular(Long empresaId, Long funcionarioId, LocalDate desligamento, String motivo) {
        Funcionario f = carregarAtivo(empresaId, funcionarioId);
        return montarCalculo(f, desligamento, motivo);
    }

    @Transactional
    public Rescisao efetivar(Long empresaId, Long funcionarioId, LocalDate desligamento, String motivo) {
        Funcionario f = carregarAtivo(empresaId, funcionarioId);
        if (f.getDataDemissao() != null || Boolean.FALSE.equals(f.getAtivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Funcionario ja desligado");
        }
        if (rescisoes.findByEmpresaIdAndFuncionarioIdAndDeletedAtIsNull(empresaId, funcionarioId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe rescisao para este funcionario");
        }

        Map<String, Object> calc = montarCalculo(f, desligamento, motivo);
        LocalDate data = (LocalDate) calc.get("dataDesligamento");

        Rescisao r = new Rescisao();
        r.setEmpresaId(empresaId);
        r.setFuncionarioId(funcionarioId);
        r.setDataDesligamento(data);
        r.setMotivo((String) calc.get("motivo"));
        r.setMesesTrabalhados(((Number) calc.get("mesesTrabalhados")).intValue());
        r.setSaldoSalario((BigDecimal) calc.get("saldoSalario"));
        r.setDecimoTerceiro((BigDecimal) calc.get("decimoTerceiro"));
        r.setFerias((BigDecimal) calc.get("ferias"));
        r.setTercoFerias((BigDecimal) calc.get("tercoFerias"));
        r.setAvisoPrevio((BigDecimal) calc.get("avisoPrevio"));
        r.setMulta40((BigDecimal) calc.get("multa40"));
        r.setTotal((BigDecimal) calc.get("total"));
        r.setStatus("EFETIVADA");
        Rescisao salva = rescisoes.save(r);

        f.setDataDemissao(data);
        f.setAtivo(false);
        funcionarios.save(f);

        return salva;
    }

    @Transactional(readOnly = true)
    public List<Rescisao> listar(Long empresaId) {
        return rescisoes.findByEmpresaIdAndDeletedAtIsNullOrderByDataDesligamentoDesc(empresaId);
    }

    private Funcionario carregarAtivo(Long empresaId, Long funcionarioId) {
        Funcionario f = funcionarios.findById(funcionarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionario inexistente"));
        if (f.getEmpresaId() == null || !f.getEmpresaId().equals(empresaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionario de outra empresa");
        }
        return f;
    }

    private Map<String, Object> montarCalculo(Funcionario f, LocalDate desligamento, String motivo) {
        if (f.getDataAdmissao() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Sem data de admissao");
        }
        if (desligamento == null) desligamento = LocalDate.now();
        if (motivo == null || motivo.isBlank()) motivo = "SEM_JUSTA_CAUSA";

        BigDecimal sal = f.getSalario() == null ? BigDecimal.ZERO : f.getSalario();
        long meses = ChronoUnit.MONTHS.between(f.getDataAdmissao().withDayOfMonth(1), desligamento.withDayOfMonth(1));
        if (meses < 0) meses = 0;
        long diasMes = desligamento.getDayOfMonth() - 1; // 0..30 (DayOfMonth >= 1)

        BigDecimal saldoSalario = sal.multiply(BigDecimal.valueOf(diasMes)).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        BigDecimal decimo = sal.multiply(BigDecimal.valueOf(Math.min(12, meses + 1))).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        BigDecimal ferias = sal.multiply(BigDecimal.valueOf(Math.min(12, meses + 1))).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        BigDecimal terco = ferias.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal aviso = "SEM_JUSTA_CAUSA".equals(motivo) ? sal : BigDecimal.ZERO;
        BigDecimal multa = "SEM_JUSTA_CAUSA".equals(motivo) ? sal.multiply(new BigDecimal("0.40")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal total = saldoSalario.add(decimo).add(ferias).add(terco).add(aviso).add(multa);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("funcionarioId", f.getId());
        m.put("dataDesligamento", desligamento);
        m.put("motivo", motivo);
        m.put("mesesTrabalhados", (int) (meses + 1));
        m.put("saldoSalario", saldoSalario);
        m.put("decimoTerceiro", decimo);
        m.put("ferias", ferias);
        m.put("tercoFerias", terco);
        m.put("avisoPrevio", aviso);
        m.put("multa40", multa);
        m.put("total", total);
        return m;
    }
}
