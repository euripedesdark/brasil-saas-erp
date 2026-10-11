package br.com.brasil_saas.fiscal.busca;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Consulta aos três cadastros fiscais, de uma vez e pela mesma porta.
 *
 * <p><b>Por que JdbcTemplate e não os repositories JPA.</b> A busca é
 * transversal: os três catálogos têm formatos de chave diferentes
 * ({@code 22030000}, {@code 01.07.01.000}, {@code 5102}) e o único predicado em
 * comum é "o código normalizado começa com isto". Espalhar isso pelos três
 * repositories significaria seis métodos novos que só a busca usa, e o ISSQN
 * ainda precisaria de um método a mais porque o código não é chave única lá:
 * são 1.759.790 linhas para 333 códigos, já que o mesmo serviço vale para
 * milhares de municípios. Aqui isso é um {@code DISTINCT} e é o mesmo código
 * para os três.
 *
 * <p>Os nomes de tabela não vêm de fora: são os três literais do enum
 * {@code Catalogo}. Não há concatenação de entrada do usuário em SQL.
 */
@Repository
public class ConsultaCatalogo {

    private final JdbcTemplate jdbc;

    public ConsultaCatalogo(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        return jdbc.query(
                "SELECT DISTINCT codigo, descricao FROM " + tabelaPermitida(tabelaFisica)
                        + " WHERE fiscal_normaliza_codigo(codigo) = ?"
                        + " OR fiscal_normaliza_codigo(codigo) LIKE ?"
                        + " ORDER BY codigo LIMIT 30",
                (rs, n) -> new Registro(rs.getString(1), rs.getString(2)),
                codigoNormalizado, codigoNormalizado + "%");
    }

    /** Código oficial exato, sem normalizar: é a junção com a palavra-chave. */
    public Optional<Registro> porCodigoExato(final String tabelaFisica, final String codigoOficial) {
        final List<Registro> r = jdbc.query(
                "SELECT DISTINCT codigo, descricao FROM " + tabelaPermitida(tabelaFisica)
                        + " WHERE codigo = ? ORDER BY codigo LIMIT 1",
                (rs, n) -> new Registro(rs.getString(1), rs.getString(2)),
                codigoOficial);
        return r.stream().findFirst();
    }
    private static String tabelaPermitida(String tabela) {
        return switch (tabela) {
            case "bc_fis_ncm" -> "bc_fis_ncm";
            case "bc_fis_issqn" -> "bc_fis_issqn";
            case "bc_fis_cfop" -> "bc_fis_cfop";
            default -> throw new IllegalArgumentException("Catalogo fiscal invalido");
        };
    }

}
