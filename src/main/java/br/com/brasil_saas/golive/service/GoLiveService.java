package br.com.brasil_saas.golive.service;

import br.com.brasil_saas.cadastro.repository.ClienteRepository;
import br.com.brasil_saas.cadastro.repository.PessoaRepository;
import br.com.brasil_saas.cadastro.repository.ProdutoRepository;
import br.com.brasil_saas.contratosvenda.repository.ContratoVendaRepository;
import br.com.brasil_saas.core.repository.EmpresaRepository;
import br.com.brasil_saas.core.service.EmpresaStripeService;
import br.com.brasil_saas.crm.repository.CrmLeadRepository;
import br.com.brasil_saas.fiscal.service.CertificadoDigitalService;
import br.com.brasil_saas.helpdesk.service.HelpdeskService;
import br.com.brasil_saas.metas.service.MetaComercialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Checklist de go-live operacional (sem reabrir emissão de nota). */
@Service
@RequiredArgsConstructor
public class GoLiveService {

    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final PessoaRepository pessoaRepository;
    private final ProdutoRepository produtoRepository;
    private final CrmLeadRepository crmLeadRepository;
    private final ContratoVendaRepository contratoVendaRepository;
    private final EmpresaStripeService stripeService;
    private final CertificadoDigitalService certificadoDigitalService;
    private final HelpdeskService helpdeskService;
    private final MetaComercialService metaComercialService;

    public Map<String, Object> checklist(Long empresaId) {
        List<Map<String, Object>> itens = new ArrayList<>();
        int ok = 0, pend = 0;

        // Empresa dados básicos
        boolean emp = existsEmpresa(empresaId);
        itens.add(item("empresa", "Cadastro da empresa", emp, "/configurar-empresa"));
        if (emp) ok++; else pend++;

        boolean stripe = false;
        try {
            var s = stripeService.obter(empresaId);
            stripe = s.configurada() && s.habilitada();
        } catch (Exception ignored) {}
        itens.add(item("stripe", "Stripe habilitado", stripe, "/configurar-empresa"));
        if (stripe) ok++; else pend++;

        boolean cert = false;
        try {
            var c = certificadoDigitalService.certificadosSalvos(empresaId);
            cert = c != null && !c.isEmpty();
        } catch (Exception ignored) {}
        itens.add(item("certificado", "Certificado A1", cert, "/configurar-empresa"));
        if (cert) ok++; else pend++;

        boolean clientes = count(() -> clienteRepository.countByEmpresaIdAndDeletedAtIsNull(empresaId)) > 0
                || count(() -> pessoaRepository.countByEmpresaIdAndDeletedAtIsNull(empresaId)) > 0;
        itens.add(item("clientes", "Clientes/pessoas", clientes, "/cadastros"));
        if (clientes) ok++; else pend++;

        boolean produtos = count(() -> produtoRepository.countByEmpresaIdAndDeletedAtIsNull(empresaId)) > 0;
        itens.add(item("produtos", "Produtos", produtos, "/cadastros"));
        if (produtos) ok++; else pend++;

        long leads = count(() -> crmLeadRepository.countByEmpresaIdAndDeletedAtIsNull(empresaId));
        itens.add(item("crm", "CRM (leads)", leads >= 0, "/crm"));
        if (leads >= 0) ok++; else pend++;

        long contratos = count(() -> contratoVendaRepository.countByEmpresaIdAndDeletedAtIsNull(empresaId));
        itens.add(item("contratos", "Contratos de venda", contratos >= 0, "/vendas/contratos"));
        if (contratos >= 0) ok++; else pend++;

        YearMonth ym = YearMonth.now();
        var meta = metaComercialService.resumo(empresaId, ym.getYear(), ym.getMonthValue());
        boolean temMeta = ((Number) meta.get("qtd")).intValue() > 0;
        itens.add(item("metas", "Metas do mês", temMeta, "/vendas/metas"));
        if (temMeta) ok++; else pend++;

        itens.add(item("helpdesk", "Helpdesk disponível", true, "/helpdesk"));
        ok++;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("empresaId", empresaId);
        out.put("ok", ok);
        out.put("pendente", pend);
        out.put("pronto", pend == 0);
        out.put("itens", itens);
        out.put("helpdesk", helpdeskService.resumo(empresaId));
        out.put("observacao", "Homologação SEFAZ/RFB/eSocial continua fora do checklist de software.");
        return out;
    }

    private Map<String, Object> item(String id, String nome, boolean ok, String rota) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nome", nome);
        m.put("status", ok ? "OK" : "PENDENTE");
        m.put("rota", rota);
        return m;
    }

    private boolean existsEmpresa(Long id) {
        try {
            return empresaRepository.existsByIdAndDeletedAtIsNull(id);
        } catch (Exception e) {
            return false;
        }
    }

    private long count(LongSupplier contador) {
        try {
            return contador.getAsLong();
        } catch (Exception e) {
            return -1;
        }
    }
}
