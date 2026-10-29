package in.prardhana.app;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** Calendar periods use local time, with a like-for-like elapsed cutoff for the previous period. */
final class SpendingPeriod {
    final String mode;
    final LocalDate start, end, previous;
    final ZoneId zone;
    final long from, until, previousUntil;
    final int buckets;

    SpendingPeriod(String mode, LocalDate anchor, ZonedDateTime now) {
        this.mode = mode;
        zone = now.getZone();
        start = switch (mode) {
            case "Week" -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "Month" -> anchor.withDayOfMonth(1);
            default -> anchor;
        };
        end = switch (mode) {
            case "Week" -> start.plusWeeks(1);
            case "Month" -> start.plusMonths(1);
            default -> start.plusDays(1);
        };
        previous = shift(-1);
        from = millis(start);
        until = Math.min(millis(end), now.toInstant().toEpochMilli() + 1);
        boolean ongoing = !now.toLocalDate().isBefore(start) && now.toLocalDate().isBefore(end);
        if (ongoing) {
            LocalDate priorDay = mode.equals("Month")
                    ? previous.withDayOfMonth(Math.min(now.getDayOfMonth(), previous.lengthOfMonth()))
                    : previous.plusDays(java.time.temporal.ChronoUnit.DAYS.between(start, now.toLocalDate()));
            previousUntil = mode.equals("Month") && now.getDayOfMonth()>previous.lengthOfMonth() ? millis(start)
                    : Math.min(millis(start), priorDay.atTime(now.toLocalTime()).atZone(zone).toInstant().toEpochMilli() + 1);
        } else previousUntil = millis(start);
        buckets = mode.equals("Day") ? 24 : (int)java.time.temporal.ChronoUnit.DAYS.between(start, end);
    }

    LocalDate shift(int amount) {
        return switch (mode) { case "Week" -> start.plusWeeks(amount); case "Month" -> start.plusMonths(amount); default -> start.plusDays(amount); };
    }
    long millis(LocalDate day) { return day.atStartOfDay(zone).toInstant().toEpochMilli(); }
    int bucket(long time, LocalDate origin) {
        ZonedDateTime date = Instant.ofEpochMilli(time).atZone(zone);
        return mode.equals("Day") ? date.getHour() : (int)java.time.temporal.ChronoUnit.DAYS.between(origin, date.toLocalDate());
    }
    String title(LocalDate today) {
        if (mode.equals("Day")) {
            if (start.equals(today)) return "Today";
            if (start.equals(today.minusDays(1))) return "Yesterday";
            return start.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH));
        }
        if (mode.equals("Month")) return start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        return start.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + " – " + end.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
    }
    String bucketLabel(int index) {
        if (mode.equals("Day")) return String.format(Locale.ENGLISH, "%02d:00", index);
        return start.plusDays(index).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH));
    }
}
