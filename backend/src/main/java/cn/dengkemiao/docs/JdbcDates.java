package cn.dengkemiao.docs;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** MySQL DATETIME maps to LocalDateTime, while other JDBC drivers use Timestamp. */
final class JdbcDates {
    private JdbcDates() {}
    static LocalDateTime local(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime time) return time;
        if (value instanceof Timestamp time) return time.toLocalDateTime();
        throw new IllegalArgumentException("Unsupported database date type: " + value.getClass().getSimpleName());
    }
}
