package in.prardhana.app;

/** Shared by SMS, inbox imports, notification capture, and manual test imports. */
public final class DuplicatePolicy {
    public static boolean same(TransactionParser.Parsed candidate, String source, long time,
            long amount, String direction, String merchant, String reference, String oldSource, long oldTime) {
        if(candidate.paise()!=amount || !candidate.direction().equals(direction)) return false;
        if(!candidate.reference().isEmpty() && !reference.isEmpty()) return candidate.reference().equalsIgnoreCase(reference);
        // Merchant/time proximity cannot distinguish two genuine rapid purchases.
        return false;
    }
}
