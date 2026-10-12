package br.com.brasil_saas.rh.service;
import br.com.brasil_saas.rh.dto.FolhaPagamentoResponse;
import br.com.brasil_saas.rh.model.FolhaPagamento;
import br.com.brasil_saas.rh.model.Funcionario;
import br.com.brasil_saas.rh.model.ItemFolhaPagamento;
import br.com.brasil_saas.rh.repository.FolhaPagamentoRepository;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
@Service @RequiredArgsConstructor
public class DecimoTerceiroService {
    private final FolhaPagamentoRepository folhas;
    private final FuncionarioRepository funcionarios;
    @Transactional
    public FolhaPagamentoResponse gerar(Long empresaId, int ano, int parcela) {
        if (parcela != 1 && parcela != 2) throw new BusinessException("Parcela invalida, use 1 ou 2");
        String competencia = "13/" + ano;
        FolhaPagamento folha = folhas.findByEmpresaIdOrderByCompetenciaDesc(empresaId).stream()
                .filter(f -> competencia.equals(f.getCompetencia()) && !"CANCELADA".equals(f.getStatus()))
                .findFirst().orElse(null);
        if (folha == null) {
            folha = new FolhaPagamento();
            folha.setEmpresaId(empresaId);
            folha.setCompetencia(competencia);
            folha.setStatus("ABERTA");
            folha = folhas.save(folha);
        }
        if ("PAGA".equals(folha.getStatus())) throw new BusinessException("Folha de 13o ja paga");
        int gerados = 0;
        for (Funcionario f : funcionarios.findByEmpresaIdAndAtivoTrue(empresaId)) {
            if (f.getSalario() == null || f.getSalario().signum() <= 0) continue;
            int meses = mesesDireito(f, ano);
            if (meses <= 0) continue;
            String marca = "13o " + parcela + "a parcela/" + ano + " (" + meses + "/12 avos)";
            boolean existe = folha.getItens().stream().anyMatch(i -> f.getId().equals(i.getFuncionarioId()) && i.getDescricao() != null && i.getDescricao().startsWith("13o " + parcela + "a parcela/" + ano));
            if (existe) continue;
            BigDecimal total = f.getSalario().multiply(BigDecimal.valueOf(meses)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            BigDecimal valor = parcela == 1 ? total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP) : total.subtract(total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
            ItemFolhaPagamento item = new ItemFolhaPagamento();
            item.setFolha(folha);
            item.setEmpresaId(empresaId);
            item.setFuncionarioId(f.getId());
            item.setTipo("PROVENTO");
            item.setDescricao(marca);
            item.setValor(valor);
            folha.getItens().add(item);
            folha.setValorTotal(folha.getValorTotal().add(valor));
            gerados++;
        }
        folhas.save(folha);
        return FolhaPagamentoResponse.from(folha);
    }
    private int mesesDireito(Funcionario f, int ano) {
        if (f.getDataAdmissao() == null) return 12;
        if (f.getDataAdmissao().getYear() > ano) return 0;
        if (f.getDataAdmissao().getYear() < ano) return 12;
        int meses = 13 - f.getDataAdmissao().getMonthValue();
        if (f.getDataAdmissao().getDayOfMonth() > 15) meses--;
        return meses; // ja >= 0 pela conta dos meses no ano
    }
}
