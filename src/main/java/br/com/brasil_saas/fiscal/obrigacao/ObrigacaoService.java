package br.com.brasil_saas.fiscal.obrigacao;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class ObrigacaoService {
    private final FisObrigacaoRepository repo;
    private final FisObrigacaoEntregaRepository entregas;
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    private void garantir(Optional<?> o, String msg) {
        o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    public List<FisObrigacao> catalogo(Long empresaId) { return repo.findByEmpresaIdAndDeletedAtIsNull(empresaId); }
    @Transactional public FisObrigacao salvar(Long empresaId, FisObrigacao o) {
        o.setId(null);
        if (o.getPeriodicidade() == null) o.setPeriodicidade("MENSAL");
        if (o.getDiaVencimento() == null) o.setDiaVencimento(20);
        if (o.getAtiva() == null) o.setAtiva(true);
        return repo.save(o);
    }
    @Transactional public List<FisObrigacao> instalarModelo(Long empresaId) {
        List<String[]> modelo = List.of(new String[]{"GIA-ICMS", "SEFAZ", "MENSAL", "20"}, new String[]{"SPED Fiscal ICMS/IPI", "SEFAZ", "MENSAL", "15"}, new String[]{"EFD-Contribuicoes", "RFB", "MENSAL", "10"}, new String[]{"EFD-Reinf", "RFB", "MENSAL", "15"}, new String[]{"DCTFWeb", "RFB", "MENSAL", "20"}, new String[]{"DAS Simples", "RFB", "MENSAL", "20"}, new String[]{"eSocial fechamento", "eSocial", "MENSAL", "15"});
        List<FisObrigacao> out = new ArrayList<>();
        for (String[] m : modelo) {
            boolean existe = repo.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream().anyMatch(x -> m[0].equalsIgnoreCase(x.getNome()));
            if (existe) continue;
            FisObrigacao o = new FisObrigacao();
            o.setNome(m[0]);
            o.setOrgao(m[1]);
            o.setPeriodicidade(m[2]);
            o.setDiaVencimento(Integer.parseInt(m[3]));
            o.setAtiva(true);
            out.add(repo.save(o));
        }
        return out;
    }
    public List<Map<String, Object>> agenda(Long empresaId, String competencia) {
        if (competencia == null || competencia.isBlank()) { LocalDate h = LocalDate.now(); competencia = h.getYear() + "-" + String.format("%02d", h.getMonthValue()); }
        final String comp = competencia;
        List<Map<String, Object>> out = new ArrayList<>();
        for (FisObrigacao o : repo.findByEmpresaIdAndDeletedAtIsNull(empresaId)) {
            if (!Boolean.TRUE.equals(o.getAtiva())) continue;
            var ent = entregas.findByObrigacaoIdAndEmpresaIdAndCompetenciaAndDeletedAtIsNull(o.getId(), empresaId, comp);
            String status = ent.map(FisObrigacaoEntrega::getStatus).orElse("PENDENTE");
            LocalDate venc = null;
            try { venc = LocalDate.of(Integer.parseInt(comp.substring(0, 4)), Integer.parseInt(comp.substring(5, 7)), Math.min(28, o.getDiaVencimento() == null ? 20 : o.getDiaVencimento())); } catch (Exception ignored) {}
            boolean atrasada = "PENDENTE".equals(status) && venc != null && venc.isBefore(LocalDate.now());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("obrigacaoId", o.getId());
            m.put("nome", o.getNome());
            m.put("orgao", o.getOrgao());
            m.put("vencimento", venc == null ? null : venc.toString());
            m.put("status", atrasada ? "ATRASADA" : status);
            m.put("protocolo", ent.map(FisObrigacaoEntrega::getProtocolo).orElse(null));
            out.add(m);
        }
        return out;
    }
    @Transactional public FisObrigacaoEntrega entregar(Long empresaId, Long obrigacaoId, String competencia, String protocolo) {
        garantir(repo.findByIdAndEmpresaIdAndDeletedAtIsNull(obrigacaoId, empresaId), "Obrigacao inexistente");
        FisObrigacaoEntrega e = entregas.findByObrigacaoIdAndEmpresaIdAndCompetenciaAndDeletedAtIsNull(obrigacaoId, empresaId, competencia).orElseGet(FisObrigacaoEntrega::new);
        e.setObrigacaoId(obrigacaoId);
        e.setCompetencia(competencia);
        e.setStatus("ENTREGUE");
        e.setEntregueEm(LocalDateTime.now());
        e.setProtocolo(protocolo);
        return entregas.save(e);
    }
}
