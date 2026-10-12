package br.com.brasil_saas.bi.service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts legacy report placeholders to JDBC named parameters, never SQL values. */
public final class ReportSqlParameters {
    private static final Pattern PLACEHOLDER = Pattern.compile("'\\{([A-Za-z_][A-Za-z0-9_]*)}'|\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private static final Pattern WRITE_OPERATION = Pattern.compile(
            "\\b(insert|update|delete|merge|call|execute|truncate|drop|alter|create|grant|revoke|copy|refresh|vacuum|do)\\b",
            Pattern.CASE_INSENSITIVE);

    private ReportSqlParameters() {}

    /**
     * Relatorios sao consultas de leitura. O SQL armazenado e codigo, nao um
     * valor bindavel; por isso ele precisa ser validado antes de executar.
     * Valores variaveis continuam sendo enviados separadamente ao JDBC.
     */
    /** Valida que o SQL e somente-leitura (SELECT/WITH, sem escrita, sem encadeamento). */
    public static void validate(String sql) {
        validateReadOnly(sql);
    }

    private static void validateReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL do relatorio vazio");
        }

        String normalized = sql.strip();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!Pattern.compile("^(SELECT|WITH)\\\\b").matcher(upper).find()) {
            throw new IllegalArgumentException("Relatorios aceitam somente consultas SELECT ou WITH");
        }
        if (normalized.indexOf(';') >= 0 || normalized.contains("--")
                || normalized.contains("/*") || normalized.contains("*/")) {
            throw new IllegalArgumentException("SQL do relatorio nao pode conter comandos encadeados ou comentarios");
        }

        // Remove literais para nao confundir texto de resultado com palavras SQL.
        String semLiterais = normalized.replaceAll("(?s)'(?:''|[^'])*'", "''")
                .replaceAll("\\\"(?:\\\"\\\"|[^\\\"])*\\\"", "\\\"\\\"");
        if (WRITE_OPERATION.matcher(semLiterais).find()) {
            throw new IllegalArgumentException("SQL do relatorio deve ser somente leitura");
        }
    }

    public static String bind(String sql, Map<String, ?> parameters) {
        validateReadOnly(sql);
        Matcher matcher = PLACEHOLDER.matcher(sql);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (parameters == null || !parameters.containsKey(name)) {
                throw new IllegalArgumentException("Parametro de relatorio ausente: " + name);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(":" + name));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
