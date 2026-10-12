package br.com.brasil_saas.fiscal.service;

import br.com.brasil_saas.fiscal.model.Apuracao;
import br.com.brasil_saas.fiscal.model.Reinf;
import br.com.brasil_saas.fiscal.repository.ApuracaoRepository;
import br.com.brasil_saas.fiscal.repository.NfeRepository;
import br.com.brasil_saas.fiscal.repository.NfseRepository;
import br.com.brasil_saas.fiscal.repository.ReinfRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Painel consolidado: apurações, REINF, regras, NFe do período. */
@Service
@RequiredArgsConstructor
public class FiscalPainelService {

    private final ApuracaoRepository apuracaoRepository;
    private final ReinfRepository reinfRepository;
    private final RegraTributariaService regraService;
    private final NfeRepository nfeRepository;
    private final NfseRepository nfseRepository;

    public Map<String, Object> resumo(Long empresaId, String competencia) {
        String comp = competencia == null || competencia.isBlank()
                ? YearMonth.now().toString()
                : competencia.trim();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("competencia", comp);

        List<Apuracao> aps = apuracaoRepository.findByEmpresaIdAndCompetencia(empresaId, comp);
        out.put("apuracoes", aps);
        out.put("apuracoesCount", aps.size());
        out.put("apuracoesEncerradas", aps.stream().filter(a -> "ENCERRADA".equalsIgnoreCase(a.getStatus())).count());

        List<Reinf> reinf = reinfRepository.findByEmpresaIdAndCompetencia(empresaId, comp);
        out.put("reinf", reinf);
        out.put("reinfCount", reinf.size());

        out.put("regrasAtivas", regraService.listar(empresaId).stream().filter(r -> Boolean.TRUE.equals(r.getAtiva())).count());

        LocalDate ini = YearMonth.parse(comp).atDay(1);
        LocalDate fim = YearMonth.parse(comp).atEndOfMonth();
        java.time.LocalDateTime de = ini.atStartOfDay();
        java.time.LocalDateTime ate = fim.plusDays(1).atStartOfDay();
        try {
            out.put("nfePeriodo", nfeRepository.countNoPeriodo(empresaId, de, ate));
        } catch (Exception e) {
            out.put("nfePeriodo", null);
            out.put("nfeErro", e.getMessage());
        }
        try {
            out.put("nfsePeriodo", nfseRepository.countNoPeriodo(empresaId, de, ate));
        } catch (Exception e) {
            out.put("nfsePeriodo", null);
        }
        return out;
    }
}
