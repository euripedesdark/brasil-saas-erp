package br.com.brasil_saas.shared.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Operacoes de banco para o gerenciador SQL do SUPERUSER (estilo Interbase).
 *
 * Regra central: NENHUM identificador (tabela, coluna, esquema) entra no SQL
 * por concatenacao de string. Tudo passa por {@link #identificador(String)},
 * que so aceita o que o proprio banco ja possui — nome de tabela, coluna ou
 * view do schema. E por isso que {@code update-campo} trabalha com metadados
 * resolvidos, e nao com o que o navegador mandou.
 *
 * O console antigo aceitava {@code tableName} e {@code column} direto do
 * cliente e montava {@code String.format("UPDATE %s SET %s = ?")}: qualquer
 * um autenticado como ADMIN escrevia {@code ; DROP TABLE ...}. Aqui nao ha
 * caminho para isso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GerenciadorSqlService {

    private final JdbcTemplate jdbc;

    private static final String SCHEMA = "brasil_saas";

    /** Nomes de objeto validos no Postgres: ASCII, inicia com letra/underscore. */
    private static final Pattern IDENTIFICADOR = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");

    /** Statements que so leem. Anything else exige confirmacao explicita. */
    private static final Pattern SO_LEITURA = Pattern.compile(
            "^\\s*(select|with|show|explain|\\(|table)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern QUALQUER_ESCRITA = Pattern.compile(
            "\\b(insert|update|delete|truncate|drop|alter|create|grant|revoke|merge|comment|vacuum|analyze|refresh|copy|reindex|cluster)\\b",
            Pattern.CASE_INSENSITIVE);

    // ---------------- catalogo ----------------

    public List<Map<String, Object>> listarTabelas() {
        return jdbc.queryForList("""
                select table_name,
                       (select count(*) from information_schema.columns c
                         where c.table_schema = t.table_schema
                           and c.table_name   = t.table_name) as colunas
                  from information_schema.tables t
                 where t.table_schema = ?
                   and t.table_type = 'BASE TABLE'
                 order by t.table_name
                """, SCHEMA);
    }

    public List<Map<String, Object>> listarViews() {
        return jdbc.queryForList("""
                select table_name as view_name
                  from information_schema.views
                 where table_schema = ?
                 order by table_name
                """, SCHEMA);
    }

    /** Estrutura da tabela: colunas, tipos, nulabilidade, chave primaria, defaults. */
    public List<Map<String, Object>> descreverTabela(String tabela) {
        String t = identificador(tabela);
        return jdbc.queryForList("""
                select c.column_name,
                       c.data_type,
                       c.character_maximum_length as tamanho_max,
                       c.is_nullable = 'YES' as aceita_nulo,
                       c.column_default            as valor_padrao,
                       (select string_agg(k.column_name, ', ' order by k.ordinal_position)
                          from information_schema.key_column_usage k
                          join information_schema.table_constraints tc
                            on tc.constraint_name = k.constraint_name
                           and tc.table_schema = k.table_schema
                         where k.table_schema = c.table_schema
                           and k.table_name = c.table_name
                           and tc.constraint_type = 'PRIMARY KEY'
                           and k.column_name = c.column_name) is not null as chave_primaria
                  from information_schema.columns c
                 where c.table_schema = ? and c.table_name = ?
                 order by c.ordinal_position
                """, SCHEMA, t);
    }

    public long contarRegistros(String tabela) {
        String t = identificador(tabela);
        Long n = jdbc.queryForObject("select count(*) from " + qualificar(t), Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Le paginada com busca opcional, sem montar o WHERE a mao: o filtro entra
     * como parametro e o nome da coluna vem do catalogo.
     */
    public Map<String, Object> navegar(String tabela, Integer pagina, Integer tamanho, String busca, String colunaBusca) {
        String t = identificador(tabela);
        String colunas = String.join(", ",
                descreverTabela(t).stream()
                        .map(c -> identificador((String) c.get("column_name")))
                        .toList());

        // LIMIT/OFFSET sao concatenados como literal, nunca como bind: no
        // Postgres o parametro em LIMIT e se lido como bigint e a declaracao
        // prepared falha. Sao inteiros ja validados por faixa, entao nao ha
        // como injetar algo aqui.
        int limite = Math.min(Math.max(inteiro(tamanho, 50), 1), 1000);
        int paginaSegura = Math.max(inteiro(pagina, 0), 0);
        long offset = (long) paginaSegura * limite;

        String where = "";
        List<Object> params = new ArrayList<>();
        if (busca != null && !busca.isBlank() && colunaBusca != null && !colunaBusca.isBlank()) {
            String cb = identificador(colunaBusca);
            // a coluna precisa existir, senao o filtro vira erro de runtime
            boolean existe = descreverTabela(t).stream()
                    .anyMatch(c -> cb.equals(c.get("column_name")));
            if (existe) {
                where = " where " + cb + "::text ilike ?";
                params.add("%" + busca.trim() + "%");
            }
        }

        List<Map<String, Object>> linhas =
                jdbc.queryForList("select * from " + qualificar(t) + where
                                + " limit " + limite + " offset " + offset,
                        params.toArray());

        return Map.of(
                "tabela", t,
                "linhas", linhas,
                "total", contarRegistros(t),
                "pagina", paginaSegura,
                "tamanho", limite);
    }

    // ---------------- execucao ----------------

    /**
     * Roda um comando.
     *
     * @param confirmarDestructive o cliente precisa marcar explicitamente; sem
     *                            isso, qualquer statement que nao seja somente
     *                            leitura e recusado com 409.
     */
    public Resultado executar(String sql, boolean confirmarDestructive, String usuario) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Comando vazio");
        }
        String comando = sql.strip();
        if (comando.endsWith(";")) {
            comando = comando.substring(0, comando.length() - 1).strip();
        }

        boolean temEscrita = QUALQUER_ESCRITA.matcher(comando).find();
        boolean soLeitura = SO_LEITURA.matcher(comando).find() && !temEscrita;

        if (!soLeitura && !confirmarDestructive) {
            throw new ConfirmacaoNecessariaException(
                    "Este comando altera dados. Envie confirmacao explicita para executar.");
        }

        log.warn("SQL executado por usuario='{}' soLeitura={} sql={}",
                usuario, soLeitura, resumo(comando));

        long inicio = System.currentTimeMillis();
        if (soLeitura) {
            List<Map<String, Object>> linhas = jdbc.queryForList(comando);
            long ms = System.currentTimeMillis() - inicio;
            return new Resultado(true, linhas, linhas.size(), ms, null);
        }

        int afetadas = jdbc.update(comando);
        long ms = System.currentTimeMillis() - inicio;
        return new Resultado(true, List.of(), afetadas, ms, null);
    }

    /**
     * Executa um script com varios comandos separados por ';' e devolve um
     * resultado por comando. Nao e transacional: cada comando e seu proprio.
     */
    public List<Resultado> executarScript(String script, boolean confirmarDestructive, String usuario) {
        List<Resultado> saida = new ArrayList<>();
        for (String parte : dividir(script)) {
            if (parte.isBlank()) continue;
            saida.add(executar(parte, confirmarDestructive, usuario));
        }
        return saida;
    }

    /**
     * UPDATE de um campo, resolvendo tabela e coluna contra o catalogo.
     * Se nao existirem no schema, o metodo nem chega a montar o SQL.
     */
    public Resultado atualizarCampo(String tabela, String coluna, Object valor, Object id, String pk,
                                   String usuario) {
        String t = identificador(tabela);
        List<Map<String, Object>> estrutura = descreverTabela(t);

        String c = identificador(coluna);
        if (estrutura.stream().noneMatch(x -> c.equals(x.get("column_name")))) {
            throw new ColunaInvalidaException("A tabela " + t + " nao tem a coluna " + c);
        }

        String chave = identificador(pk == null || pk.isBlank() ? "id" : pk);
        if (estrutura.stream().noneMatch(x -> chave.equals(x.get("column_name")))) {
            throw new ColunaInvalidaException("A tabela " + t + " nao tem a chave " + chave);
        }

        String sql = "update " + qualificar(t) + " set " + c + " = ? where " + chave + " = ?";
        int afetadas = jdbc.update(sql, valor, id);

        log.warn("Campo alterado por usuario='{}' tabela={} coluna={} linhas={}", usuario, t, c, afetadas);
        return new Resultado(true, List.of(), afetadas, 0, null);
    }

    public Map<String, Object> estatisticas() {
        return jdbc.queryForMap("""
                select (select count(*) from information_schema.tables
                         where table_schema = ? and table_type = 'BASE TABLE') as tabelas,
                       (select count(*) from information_schema.views
                         where table_schema = ?) as views,
                       (select count(*) from pg_stat_user_tables) as tabelas_com_uso,
                       (select count(*) from pg_stat_activity) as conexoes_ativas,
                       pg_size_pretty(pg_database_size(current_database())) as tamanho_banco
                """, SCHEMA, SCHEMA);
    }

    // ---------------- apoio ----------------

    /**
     * Unico portao por onde um identificador entra no SQL.
     * Nao normaliza: se o nome for invalido, e recusado.
     */
    private String identificador(String nome) {
        if (nome == null || !IDENTIFICADOR.matcher(nome).matches()) {
            throw new IdentificadorInvalidoException("Identificador invalido: " + nome);
        }
        return nome;
    }

    private String qualificar(String tabela) {
        return SCHEMA + "." + tabela;
    }

    /** Converte para int, devolvendo o padrao quando nao da. */
    private static int inteiro(Integer valor, int padrao) {
        return valor == null ? padrao : valor;
    }

    /** Divide por ';' respeitando aspas simples, para nao cortar dentro de string. */
    private List<String> dividir(String script) {
        List<String> partes = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean emAspas = false;
        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            if (ch == '\'') {
                emAspas = !emAspas;
            }
            if (ch == ';' && !emAspas) {
                partes.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(ch);
            }
        }
        partes.add(atual.toString());
        return partes;
    }

    private String resumo(String sql) {
        String s = sql.replaceAll("\\s+", " ").strip();
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    public record Resultado(boolean ok, List<Map<String, Object>> linhas,
                            int afetadas, long tempoMs, String erro) {
    }

    public static class IdentificadorInvalidoException extends IllegalArgumentException {
        public IdentificadorInvalidoException(String m) {
            super(m);
        }
    }

    public static class ColunaInvalidaException extends IllegalArgumentException {
        public ColunaInvalidaException(String m) {
            super(m);
        }
    }

    /** HTTP 409: o comando altera dados e faltou a confirmacao. */
    public static class ConfirmacaoNecessariaException extends RuntimeException {
        public ConfirmacaoNecessariaException(String m) {
            super(m);
        }
    }
}
