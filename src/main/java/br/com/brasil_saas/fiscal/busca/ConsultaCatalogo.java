package br.com.brasil_saas.fiscal.busca;

import br.com.brasil_saas.fiscal.repository.CfopRepository;
import br.com.brasil_saas.fiscal.repository.IssqnRepository;
import br.com.brasil_saas.fiscal.repository.NcmRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.util.ArrayList;
import java.util.function.Function;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Consulta aos três cadastros fiscais, de uma vez e pela mesma porta.
 *
 * A consulta usa repositories Spring Data JPA; a normalização ocorre em Java.
 */
@Repository
public class ConsultaCatalogo {

    private final NcmRepository ncmRepository;
    private final IssqnRepository issqnRepository;
    private final CfopRepository cfopRepository;

    public ConsultaCatalogo(final NcmRepository ncmRepository,
                            final IssqnRepository issqnRepository,
                            final CfopRepository cfopRepository) {
        this.ncmRepository = ncmRepository;
        this.issqnRepository = issqnRepository;
        this.cfopRepository = cfopRepository;
    }

    /** Uma linha do cadastro fiscal. */
    public record Registro(String codigo, String descricao) {
    }

    /**
     * Busca usando apenas Spring Data JPA para paginação e Java para normalização.
     * Mantém a ordem por código e interrompe a leitura após 30 resultados.
     * Sem SQL nativo, funções de banco ou alterações de schema.
     */
    public List<Registro> porCodigo(final String tabelaFisica, final String codigoNormalizado) {
        if (codigoNormalizado == null || codigoNormalizado.isEmpty()) {
            return List.of();
        }
        return switch (catalogo(tabelaFisica)) {
            case NCM -> buscarPaginado(ncmRepository::findAllByOrderByCodigo,
                    n -> new Registro(n.getCodigo(), n.getDescricao()), codigoNormalizado);
            case ISSQN -> buscarPaginado(issqnRepository::findAllByOrderByCodigo,
                    i -> new Registro(i.getCodigo(), i.getDescricao()), codigoNormalizado)
                    .stream().distinct().toList();
            case CFOP -> buscarPaginado(cfopRepository::findAllByOrderByCodigo,
                    f -> new Registro(f.getCodigo(), f.getDescricao()), codigoNormalizado);
        };
    }

    private static <T> List<Registro> buscarPaginado(
            final Function<PageRequest, Page<T>> carregar,
            final Function<T, Registro> mapear,
            final String codigoNormalizado) {
        final List<Registro> encontrados = new ArrayList<>(30);
        int pagina = 0;
        Page<T> lote;
        do {
            lote = carregar.apply(PageRequest.of(pagina++, 200));
            for (T entidade : lote.getContent()) {
                Registro registro = mapear.apply(entidade);
                if (NormalizacaoFiscal.codigo(registro.codigo()).startsWith(codigoNormalizado)) {
                    encontrados.add(registro);
                    if (encontrados.size() == 30) {
                        return encontrados;
                    }
                }
            }
        } while (lote.hasNext());
        return encontrados;
    }

    /** Código oficial exato, sem normalizar: é a junção com a palavra-chave. */
    public Optional<Registro> porCodigoExato(final String tabelaFisica, final String codigoOficial) {
        final List<Registro> r = switch (catalogo(tabelaFisica)) {
            case NCM -> ncmRepository.buscarPorCodigoExato(codigoOficial, PageRequest.of(0, 1))
                    .stream().map(n -> new Registro(n.getCodigo(), n.getDescricao())).toList();
            case ISSQN -> issqnRepository.buscarPorCodigoExato(codigoOficial, PageRequest.of(0, 1))
                    .stream().map(i -> new Registro(i.getCodigo(), i.getDescricao())).toList();
            case CFOP -> cfopRepository.buscarPorCodigoExato(codigoOficial, PageRequest.of(0, 1))
                    .stream().map(c -> new Registro(c.getCodigo(), c.getDescricao())).toList();
        };
        return r.stream().findFirst();
    }

    private enum Catalogo {
        NCM, ISSQN, CFOP
    }

    private static Catalogo catalogo(String tabela) {
        return switch (tabela) {
            case "bc_fis_ncm" -> Catalogo.NCM;
            case "bc_fis_issqn" -> Catalogo.ISSQN;
            case "bc_fis_cfop" -> Catalogo.CFOP;
            default -> throw new IllegalArgumentException("Catalogo fiscal invalido");
        };
    }
}
