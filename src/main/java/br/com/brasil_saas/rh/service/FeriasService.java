package br.com.brasil_saas.rh.service;
import br.com.brasil_saas.rh.model.Ferias;
import br.com.brasil_saas.rh.model.FolhaPagamento;
import br.com.brasil_saas.rh.model.Funcionario;
import br.com.brasil_saas.rh.model.ItemFolhaPagamento;
import br.com.brasil_saas.rh.repository.FeriasRepository;
import br.com.brasil_saas.rh.repository.FolhaPagamentoRepository;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
@Service @RequiredArgsConstructor
public class FeriasService {
    private final FeriasRepository ferias;
    private final FuncionarioRepository funcionarios;
    private final FolhaPagamentoRepository folhas;
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    @Transactional(readOnly = true)
    public List<Ferias> listar(Long empresaId) { return ferias.findByEmpresaIdAndDeletedAtIsNullOrderByDataInicioDesc(empresaId); }
    @Transactional
    public Ferias programar(Long empresaId, Long funcionarioId, LocalDate inicio, Integer dias, String observacao) {
        Funcionario f = funcionarios.findById(funcionarioId).filter(x -> empresaId.equals(x.getEmpresaId())).orElseThrow(() -> new ResourceNotFoundException("funcionario", String.valueOf(funcionarioId)));
        if (!Boolean.TRUE.equals(f.getAtivo())) throw new BusinessException("Funcionario inativo");
        if (dias == null || dias < 10 || dias > 30) throw new BusinessException("Ferias de 10 a 30 dias");
        if (inicio == null) throw new BusinessException("Data de inicio obrigatoria");
        LocalDate fim = inicio.plusDays(dias - 1);
        for (Ferias e : ferias.findByEmpresaIdAndFuncionarioIdAndDeletedAtIsNullOrderByDataInicioDesc(empresaId, funcionarioId)) {
            if ("CANCELADA".equals(e.getStatus())) continue;
            if (!inicio.isAfter(e.getDataFim()) && !fim.isBefore(e.getDataInicio())) throw new BusinessException("Sobrepoe ferias de " + e.getDataInicio() + " a " + e.getDataFim());
        }
        Ferias n = new Ferias();
        n.setEmpresaId(empresaId);
        n.setFuncionarioId(funcionarioId);
        n.setDataInicio(inicio);
        n.setDias(dias);
        n.setDataFim(fim);
        n.setStatus("PROGRAMADA");
        n.setObservacao(observacao);
        return ferias.save(n);
    }
    @Transactional
    public Ferias gozar(Long empresaId, Long id, Long folhaId) {
        Ferias e = ferias.findById(id).filter(x -> empresaId.equals(x.getEmpresaId())).orElseThrow(() -> new ResourceNotFoundException("ferias", String.valueOf(id)));
        if (!"PROGRAMADA".equals(e.getStatus())) throw new BusinessException("Somente ferias PROGRAMADA pode ser gozada");
        FolhaPagamento folha = folhas.findById(folhaId).filter(x -> empresaId.equals(x.getEmpresaId())).orElseThrow(() -> new ResourceNotFoundException("folha", String.valueOf(folhaId)));
        if ("PAGA".equals(folha.getStatus()) || "CANCELADA".equals(folha.getStatus())) throw new BusinessException("Folha fechada");
        Funcionario f = funcionarios.findById(e.getFuncionarioId()).orElseThrow(() -> new ResourceNotFoundException("funcionario", String.valueOf(e.getFuncionarioId())));
        BigDecimal sal = f.getSalario() == null ? BigDecimal.ZERO : f.getSalario();
        BigDecimal valor = sal.multiply(BigDecimal.valueOf(e.getDias())).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        BigDecimal terco = valor.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        String per = "Ferias " + e.getDataInicio().format(DIA) + " a " + e.getDataFim().format(DIA);
        adicionar(folha, empresaId, e.getFuncionarioId(), per, valor);
        adicionar(folha, empresaId, e.getFuncionarioId(), "1/3 " + per, terco);
        folhas.save(folha);
        e.setValorFerias(valor);
        e.setValorTerco(terco);
        e.setFolhaId(folha.getId());
        e.setStatus("GOZADA");
        return ferias.save(e);
    }
    @Transactional
    public Ferias cancelar(Long empresaId, Long id) {
        Ferias e = ferias.findById(id).filter(x -> empresaId.equals(x.getEmpresaId())).orElseThrow(() -> new ResourceNotFoundException("ferias", String.valueOf(id)));
        if (!"PROGRAMADA".equals(e.getStatus())) throw new BusinessException("Somente ferias PROGRAMADA pode ser cancelada");
        e.setStatus("CANCELADA");
        return ferias.save(e);
    }
    private void adicionar(FolhaPagamento folha, Long empresaId, Long funcionarioId, String descricao, BigDecimal valor) {
        ItemFolhaPagamento item = new ItemFolhaPagamento();
        item.setFolha(folha);
        item.setEmpresaId(empresaId);
        item.setFuncionarioId(funcionarioId);
        item.setTipo("PROVENTO");
        item.setDescricao(descricao);
        item.setValor(valor);
        folha.getItens().add(item);
        folha.setValorTotal(folha.getValorTotal().add(valor));
    }
}
