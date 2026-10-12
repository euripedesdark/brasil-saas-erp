package br.com.brasil_saas.portais.service.impl;
import br.com.brasil_saas.cadastro.model.Pessoa;
import br.com.brasil_saas.cadastro.repository.ClienteRepository;
import br.com.brasil_saas.cadastro.repository.FornecedorRepository;
import br.com.brasil_saas.cadastro.repository.PessoaRepository;
import br.com.brasil_saas.compras.repository.PedidoCompraRepository;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import br.com.brasil_saas.portais.model.*;
import br.com.brasil_saas.portais.repository.*;
import br.com.brasil_saas.portais.service.PortalService;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class PortalServiceImpl implements PortalService {
    private final PtlAcessoRepository acessos;
    private final PessoaRepository pessoas;
    private final ClienteRepository clientes;
    private final FornecedorRepository fornecedores;
    private final TituloRepository titulos;
    private final PedidoVendaRepository pedidosVenda;
    private final PedidoCompraRepository pedidosCompra;
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    @Override public List<PtlAcesso> acessos(Long empresaId) {
        return acessos.findByEmpresaIdAndDeletedAtIsNull(empresaId);
    }
    @Override @Transactional public PtlAcesso gerar(Long empresaId, Long userId, String tipo, Long pessoaId, Integer diasValidade) {
        List<String> tipos = List.of("CLIENTE", "FORNECEDOR", "FUNCIONARIO");
        if (!tipos.contains(tipo)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Tipo invalido");
        Pessoa p = exigir(pessoas.findById(pessoaId), "Pessoa inexistente");
        if (p.getEmpresaId() == null || !p.getEmpresaId().equals(empresaId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pessoa de outra empresa");
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        PtlAcesso a = new PtlAcesso();
        a.setTipo(tipo);
        a.setPessoaId(pessoaId);
        a.setToken(sb.toString());
        a.setExpiraEm(LocalDateTime.now().plusDays(diasValidade == null || diasValidade <= 0 ? 30 : diasValidade));
        a.setAtivo(true);
        return acessos.save(a);
    }
    @Override @Transactional public void revogar(Long empresaId, Long id) {
        PtlAcesso a = exigir(acessos.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Acesso inexistente");
        a.setAtivo(false);
        acessos.save(a);
    }
    private PtlAcesso exigirToken(String token) {
        PtlAcesso a = exigir(acessos.findByTokenAndDeletedAtIsNull(token), "Token invalido");
        if (!Boolean.TRUE.equals(a.getAtivo()) || a.getExpiraEm().isBefore(LocalDateTime.now())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expirado ou revogado");
        return a;
    }
    @Override @Transactional public Map<String, Object> validar(String token) {
        PtlAcesso a = exigirToken(token);
        a.setUltimoUsoEm(LocalDateTime.now());
        acessos.save(a);
        Pessoa p = exigir(pessoas.findById(a.getPessoaId()), "Pessoa inexistente");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", a.getTipo());
        m.put("nome", p.getNome());
        m.put("expiraEm", a.getExpiraEm().toString());
        return m;
    }
    @Override public Map<String, Object> minhaConta(String token) {
        PtlAcesso a = exigirToken(token);
        Long empresaId = a.getEmpresaId();
        Pessoa p = exigir(pessoas.findById(a.getPessoaId()), "Pessoa inexistente");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", a.getTipo());
        m.put("nome", p.getNome());
        m.put("documento", p.getDocumento());
        String tipo = a.getTipo() == null ? "" : a.getTipo();
        boolean verTitulos = "CLIENTE".equals(tipo) || "FORNECEDOR".equals(tipo);
        if (verTitulos) {
            List<Map<String, Object>> tit = new ArrayList<>();
            for (Titulo t : titulos.findByEmpresaIdAndDeletedAtIsNullOrderByDataVencimento(empresaId)) {
                if (!a.getPessoaId().equals(t.getPessoaId())) continue;
                boolean aberto = "ABERTO".equals(t.getStatus()) || "PARCIAL".equals(t.getStatus());
                if (!aberto) continue;
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", t.getId());
                x.put("descricao", t.getDescricao());
                x.put("vencimento", t.getDataVencimento() == null ? null : t.getDataVencimento().toString());
                x.put("saldo", t.getValorSaldo());
                x.put("status", t.getStatus());
                tit.add(x);
            }
            m.put("titulos", tit);
        }
        if ("CLIENTE".equals(tipo)) {
            List<Map<String, Object>> peds = new ArrayList<>();
            var cli = clientes.findByEmpresaIdAndPessoaIdAndDeletedAtIsNull(empresaId, a.getPessoaId());
            if (cli.isPresent()) {
                for (var pv : pedidosVenda.findByEmpresaIdAndClienteId(empresaId, cli.get().getId())) {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("id", pv.getId());
                    x.put("status", pv.getStatus() == null ? "" : pv.getStatus());
                    peds.add(x);
                }
            }
            m.put("pedidos", peds);
        }
        if ("FORNECEDOR".equals(tipo)) {
            List<Map<String, Object>> peds = new ArrayList<>();
            var forn = fornecedores.findByEmpresaIdAndPessoaIdAndDeletedAtIsNull(empresaId, a.getPessoaId());
            if (forn.isPresent()) {
                for (var pc : pedidosCompra.findByEmpresaIdOrderByDataEmissaoDesc(empresaId)) {
                    if (!forn.get().getId().equals(pc.getFornecedorId())) continue;
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("id", pc.getId());
                    x.put("status", pc.getStatus() == null ? "" : pc.getStatus());
                    peds.add(x);
                }
            }
            m.put("pedidos", peds);
        }
        return m;
    }
}
