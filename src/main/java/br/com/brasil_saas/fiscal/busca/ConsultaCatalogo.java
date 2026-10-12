package br.com.brasil_saas.fiscal.busca;

import br.com.brasil_saas.fiscal.repository.CfopRepository;
import br.com.brasil_saas.fiscal.repository.IssqnRepository;
import br.com.brasil_saas.fiscal.repository.NcmRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Consulta aos três cadastros fiscais, de uma vez e pela mesma porta.
 *
 * <p><b>Por que JPA e não os repositories separados antes.</b> A busca é
 * transversal: os três catálogos têm formatos de chave diferentes
 * ({@code 22030000}, {@code 01.07.01.000}, {@code 5102}) e o único predicado em
 * comum é "o código normalizado começa com isto". Cada catálogo tem seu
 * repository JPA; a normalização usa a mesma função
 * {@code fiscal_normaliza_codigo} do banco via JPQL {@code FUNCTION}, com
 * parâmetros vinculados — sem concatenação de entrada do usuário em SQL.
 *
 * <p>Os nomes de tabela não vêm de fora: são os três literais do enum
 * {@code Catalogo}. Não há concatenação de entrada do usuário em SQL.
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
     * Códigos cujo formato normalizado casa exatamente, ou começa com, o termo.
     * A comparação usa {@code fiscal_normaliza_codigo} do banco — a mesma regra
     * de {@link NormalizacaoFiscal#codigo(String)}, verificada em teste.
     */
    public List<Registro> porCodigo(final String tabelaFisica, final String codigoNormalizado) {
        String prefixo = codigoNormalizado + "%";
        return switch (catalogo(tabelaFisica)) {
            case NCM -> ncmRepository
                    .buscarPorCodigoNormalizado(codigoNormalizado, prefixo, PageRequest.of(0, 30))
                    .stream().map(n -> new Registro(n.getCodigo(), n.getDescricao())).toList();
            case ISSQN -> issqnRepository
                    .buscarPorCodigoNormalizado(codigoNormalizado, prefixo, PageRequest.of(0, 30))
                    .stream().map(i -> new Registro(i.getCodigo(), i.getDescricao()))
                    .distinct().toList();
            case CFOP -> cfopRepository
                    .buscarPorCodigoNormalizado(codigoNormalizado, prefixo, PageRequest.of(0, 30))
                    .stream().map(c -> new Registro(c.getCodigo(), c.getDescricao())).toList();
        };
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
