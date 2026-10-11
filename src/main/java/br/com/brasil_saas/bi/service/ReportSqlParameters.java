package br.com.brasil_saas.bi.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts legacy report placeholders to JDBC named parameters, never SQL values. */
public final class ReportSqlParameters {
    private static final Pattern PLACEHOLDER = Pattern.compile("'\\{([A-Za-z_][A-Za-z0-9_]*)}'|\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private ReportSqlParameters() {}

    public static String bind(String sql, Map<String, ?> parameters) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL do relatorio vazio");
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
