package com.example.finConsult.service;

/** Result of a VirusTotal lookup. */
public record ScanResult(Status status, int maliciousCount, String note) {
    public enum Status {
        CLEAN,
        MALICIOUS,
        UNKNOWN,
        SKIPPED
    }

    public static ScanResult clean() {
        return new ScanResult(Status.CLEAN, 0, null);
    }
    public static ScanResult malicious(int count)  {
        return new ScanResult(Status.MALICIOUS, count, null);
    }
    public static ScanResult unknown()             {
        return new ScanResult(Status.UNKNOWN, 0, "not in VirusTotal");
    }
    public static ScanResult skipped(String reason)   {
        return new ScanResult(Status.SKIPPED, 0, reason);
    }
}
