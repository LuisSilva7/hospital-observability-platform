package pt.uminho.hop.common;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(
        int status,
        String error,
        String message,
        List<String> details,
        OffsetDateTime timestamp
) {
    public static ApiError of(int status, String error, String message, List<String> details) {
        return new ApiError(status, error, message, details, OffsetDateTime.now());
    }
}
