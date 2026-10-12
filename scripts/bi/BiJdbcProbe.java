import java.sql.*;
import java.util.Properties;

/** Executado com java --class-path postgresql.jar BiJdbcProbe.java host porta certificados. */
class BiJdbcProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("host porta diretorio-certificados");
        Properties p = new Properties();
        p.setProperty("user", "brasil_saas_bi_reader");
        p.setProperty("sslmode", "verify-ca");
        p.setProperty("sslrootcert", args[2] + "/ca.crt");
        p.setProperty("sslcert", args[2] + "/issued/brasil_saas_bi_reader.crt");
        p.setProperty("sslkey", args[2] + "/private/brasil_saas_bi_reader.pk8");
        p.setProperty("connectTimeout", "5");
        p.setProperty("socketTimeout", "15");
        // Nenhuma propriedade password nem uso do datasource operacional.
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://" + args[0] + ":" + Integer.parseInt(args[1]) + "/brasil_saas_bi", p)) {
            c.setReadOnly(true);
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try (Statement s = c.createStatement()) {
                s.setQueryTimeout(5);
                try (ResultSet r = s.executeQuery("SELECT current_user, current_database(), "
                        + "(SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid())")) {
                    if (!r.next() || !"brasil_saas_bi_reader".equals(r.getString(1))
                            || !"brasil_saas_bi".equals(r.getString(2)) || !r.getBoolean(3))
                        throw new IllegalStateException("Identidade, banco ou TLS incorretos");
                }
                for (String area : new String[]{"financeiro", "producao", "vendas", "servicos"}) {
                    try (ResultSet r = s.executeQuery("SELECT count(*) FROM brasil_saas_dl.v_bi_" + area)) {
                        r.next(); System.out.println("JDBC " + area + " registros=" + r.getLong(1));
                    }
                    try (ResultSet r = s.executeQuery("SELECT has_table_privilege(current_user, 'bi_reporting."
                            + area + "', 'INSERT,UPDATE,DELETE,TRUNCATE')")) {
                        if (!r.next() || r.getBoolean(1)) throw new IllegalStateException("Reader possui escrita: " + area);
                    }
                }
            } finally { c.rollback(); }
        }
        System.out.println("JDBC BI: TLS, identidade dedicada, quatro views e leitura sem senha validados");
    }
}
