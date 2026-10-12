package br.com.brasil_saas.dms.service.impl;
import br.com.brasil_saas.dms.model.*;
import br.com.brasil_saas.dms.repository.*;
import br.com.brasil_saas.dms.service.DmsService;
import br.com.brasil_saas.shared.image.ImagemDocumento;
import br.com.brasil_saas.shared.image.ImagemMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class DmsServiceImpl implements DmsService {
    private final DmsDocumentoRepository documentos;
    private final DmsVersaoRepository versoes;
    private final DmsAprovacaoRepository aprovacoes;
    private final ImagemMongoRepository mongo;
    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    private static String sha256(byte[] b) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : h) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Hash indisponivel"); }
    }
    @Override public List<DmsDocumento> documentos(Long empresaId, String categoria, String status) {
        List<DmsDocumento> base = documentos.findByEmpresaIdAndDeletedAtIsNull(empresaId);
        if (categoria != null && !"".equals(categoria)) base = base.stream().filter(d -> categoria.equals(d.getCategoria())).toList();
        if (status != null && !"".equals(status)) base = base.stream().filter(d -> status.equals(d.getStatus())).toList();
        return base;
    }
    @Override @Transactional public DmsDocumento salvar(Long empresaId, DmsDocumento d) {
        d.setId(null);
        if (d.getStatus() == null) d.setStatus("RASCUNHO");
        d.setVersaoAtual(1);
        return documentos.save(d);
    }
    @Override @Transactional public DmsVersao novaVersao(Long empresaId, Long documentoId, String nomeArquivo, String contentType, byte[] conteudo, String comentario) {
        DmsDocumento d = exigir(documentos.findByIdAndEmpresaIdAndDeletedAtIsNull(documentoId, empresaId), "Documento inexistente");
        if (conteudo == null || conteudo.length == 0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Arquivo vazio");
        if (conteudo.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Arquivo acima de 25MB");
        int v = versoes.findByDocumentoIdAndEmpresaIdAndDeletedAtIsNull(documentoId, empresaId).stream().mapToInt(x -> x.getVersao() == null ? 0 : x.getVersao()).max().orElse(0) + 1;
        DmsVersao ver = new DmsVersao();
        ver.setDocumentoId(documentoId);
        ver.setVersao(v);
        ver.setArquivoNome(nomeArquivo);
        ver.setContentType(contentType);
        ver.setTamanho((long) conteudo.length);
        ver.setHash(sha256(conteudo));
        ver.setComentario(comentario);
        ver = versoes.save(ver);
        ImagemDocumento doc = new ImagemDocumento();
        doc.setEmpresaId(empresaId);
        doc.setTipoEntidade("dms-versao");
        doc.setEntidadeId(ver.getId());
        doc.setNomeArquivo(nomeArquivo);
        doc.setContentType(contentType);
        doc.setTamanho(conteudo.length);
        doc.setHash(ver.getHash());
        doc.setConteudo(conteudo);
        doc.setCriadoEm(LocalDateTime.now());
        doc.setAtualizadoEm(LocalDateTime.now());
        mongo.save(doc);
        d.setVersaoAtual(v);
        if ("RASCUNHO".equals(d.getStatus())) d.setStatus("EM_APROVACAO");
        documentos.save(d);
        return ver;
    }
    @Override public List<DmsVersao> versoes(Long empresaId, Long documentoId) {
        exigir(documentos.findByIdAndEmpresaIdAndDeletedAtIsNull(documentoId, empresaId), "Documento inexistente");
        return versoes.findByDocumentoIdAndEmpresaIdAndDeletedAtIsNull(documentoId, empresaId);
    }
    @Override public Map<String, Object> download(Long empresaId, Long versaoId) {
        DmsVersao v = exigir(versoes.findByIdAndEmpresaIdAndDeletedAtIsNull(versaoId, empresaId), "Versao inexistente");
        ImagemDocumento doc = mongo.findByEmpresaIdAndTipoEntidadeAndEntidadeId(empresaId, "dms-versao", versaoId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("arquivoNome", v.getArquivoNome());
        m.put("contentType", v.getContentType());
        m.put("tamanho", v.getTamanho());
        m.put("temConteudo", doc != null && doc.getConteudo() != null);
        return m;
    }
    public byte[] bytes(Long empresaId, Long versaoId) {
        exigir(versoes.findByIdAndEmpresaIdAndDeletedAtIsNull(versaoId, empresaId), "Versao inexistente");
        ImagemDocumento doc = exigir(mongo.findByEmpresaIdAndTipoEntidadeAndEntidadeId(empresaId, "dms-versao", versaoId), "Conteudo indisponivel");
        return doc.getConteudo();
    }
    @Override @Transactional public DmsAprovacao solicitarAprovacao(Long empresaId, Long documentoId, Integer versao, String aprovador) {
        DmsDocumento d = exigir(documentos.findByIdAndEmpresaIdAndDeletedAtIsNull(documentoId, empresaId), "Documento inexistente");
        DmsAprovacao a = new DmsAprovacao();
        a.setDocumentoId(documentoId);
        a.setVersao(versao == null ? d.getVersaoAtual() : versao);
        a.setAprovador(aprovador);
        a.setStatus("PENDENTE");
        return aprovacoes.save(a);
    }
    @Override public List<DmsAprovacao> aprovacoes(Long empresaId, Long documentoId) {
        exigir(documentos.findByIdAndEmpresaIdAndDeletedAtIsNull(documentoId, empresaId), "Documento inexistente");
        return aprovacoes.findByDocumentoIdAndEmpresaIdAndDeletedAtIsNull(documentoId, empresaId);
    }
    @Override @Transactional public DmsAprovacao decidir(Long empresaId, Long userId, Long aprovacaoId, boolean aprovar, String comentario) {
        DmsAprovacao a = exigir(aprovacoes.findByIdAndEmpresaIdAndDeletedAtIsNull(aprovacaoId, empresaId), "Aprovacao inexistente");
        if (!"PENDENTE".equals(a.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Aprovacao ja decidida");
        a.setStatus(aprovar ? "APROVADA" : "REJEITADA");
        a.setDecididoPor(userId);
        a.setDecididoEm(LocalDateTime.now());
        a.setComentario(comentario);
        aprovacoes.save(a);
        if (aprovar) {
            DmsDocumento d = exigir(documentos.findByIdAndEmpresaIdAndDeletedAtIsNull(a.getDocumentoId(), empresaId), "Documento inexistente");
            d.setStatus("APROVADO");
            documentos.save(d);
        }
        return a;
    }
    @Override public List<DmsDocumento> retencao(Long empresaId) {
        LocalDate hoje = LocalDate.now();
        return documentos.findByEmpresaIdAndDeletedAtIsNull(empresaId).stream()
            .filter(d -> d.getReterAte() != null && d.getReterAte().isBefore(hoje))
            .toList();
    }
    @Override @Transactional public void excluir(Long empresaId, Long id) {
        DmsDocumento d = exigir(documentos.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Documento inexistente");
        if (d.getReterAte() != null && d.getReterAte().isAfter(LocalDate.now())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Documento em retencao");
        for (DmsVersao v : versoes.findByDocumentoIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)) {
            mongo.findByEmpresaIdAndTipoEntidadeAndEntidadeId(empresaId, "dms-versao", v.getId()).ifPresent(mongo::delete);
            versoes.delete(v);
        }
        for (DmsAprovacao a : aprovacoes.findByDocumentoIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)) aprovacoes.delete(a);
        documentos.delete(d);
    }
}
