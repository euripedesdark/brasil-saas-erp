package br.com.brasil_saas.financeiro.service;
import br.com.brasil_saas.financeiro.model.Extrato;
import br.com.brasil_saas.financeiro.repository.ExtratoRepository;
import br.com.brasil_saas.financeiro.repository.ContaBancariaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
@Service @RequiredArgsConstructor
public class OfxService {
    private final ExtratoRepository repo;
    private final ContaBancariaRepository contas;
    @Transactional public Map<String, Object> importar(Long empresaId, Long contaBancariaId, byte[] arquivo) {
        contas.findById(contaBancariaId).filter(c -> empresaId.equals(c.getEmpresaId())).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta inexistente"));
        String txt = new String(arquivo == null ? new byte[0] : arquivo, StandardCharsets.ISO_8859_1);
        Pattern bloco = Pattern.compile("<STMTTRN>(.*?)(?=<STMTTRN>|</BANKMSGSRSV1>)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = bloco.matcher(txt);
        int novos = 0;
        int repetidos = 0;
        while (m.find()) {
            String b = m.group(1);
            String fitid = campo(b, "FITID");
            String dt = campo(b, "DTPOSTED");
            String vl = campo(b, "TRNAMT");
            String memo = campo(b, "MEMO");
            if (vl == null || vl.isBlank()) continue;
            BigDecimal valor;
            try { valor = new BigDecimal(vl.trim().replace(",", ".")); } catch (NumberFormatException ignored) { continue; }
            LocalDate data;
            try { String d = dt == null ? "" : dt.trim().substring(0, Math.min(8, dt.trim().length())); data = LocalDate.of(Integer.parseInt(d.substring(0, 4)), Integer.parseInt(d.substring(4, 6)), Integer.parseInt(d.substring(6, 8))); } catch (Exception ignored) { continue; }
            if (fitid != null && repo.existsByEmpresaIdAndContaBancariaIdAndFitidAndDeletedAtIsNull(empresaId, contaBancariaId, fitid.trim())) { repetidos++; continue; }
            Extrato e = new Extrato();
            e.setContaBancariaId(contaBancariaId);
            e.setDataMovimento(data);
            e.setDescricao(memo == null || memo.isBlank() ? "OFX" : memo.trim().substring(0, Math.min(255, memo.trim().length())));
            e.setValor(valor.abs());
            e.setTipo(valor.signum() < 0 ? "D" : "C");
            e.setFitid(fitid == null ? null : fitid.trim());
            e.setConciliado(false);
            repo.save(e);
            novos++;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("novos", novos);
        r.put("repetidos", repetidos);
        return r;
    }
    private String campo(String bloco, String tag) {
        Matcher mm = Pattern.compile("<" + tag + ">([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE).matcher(bloco);
        return mm.find() ? mm.group(1).trim() : null;
    }
}
