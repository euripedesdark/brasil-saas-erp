package br.com.brasil_saas.shared.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/** Coerção de valores vindos de {@code Map} de request para campos de entidade. */
public final class MapValues {

    private MapValues() {
    }

    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public static String required(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório: " + k);
        }
        return String.valueOf(v);
    }

    public static Long lng(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }

    public static Integer integer(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : Integer.valueOf(s);
    }

    public static BigDecimal bd(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : new BigDecimal(s);
    }

    public static Boolean bool(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v).trim());
    }

    public static LocalDate dt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (v instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(v).trim().substring(0, 10));
    }

    public static LocalDateTime dtTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime d) {
            return d;
        }
        if (v instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime();
        }
        if (v instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(v).trim());
    }
}
