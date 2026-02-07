package me.palgato.geoplugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SuspiciousActivityTracker {
    
    private final Map<String, List<Long>> blockedAttempts;
    private final int threshold;
    private final long timeWindowMillis;
    
    public SuspiciousActivityTracker(int threshold, int timeWindowMinutes) {
        this.blockedAttempts = new HashMap<>();
        this.threshold = threshold;
        this.timeWindowMillis = timeWindowMinutes * 60L * 1000L;
    }
    
    public boolean recordAttempt(String countryCode) {
        long now = Instant.now().toEpochMilli();
        
        blockedAttempts.putIfAbsent(countryCode, new ArrayList<>());
        List<Long> attempts = blockedAttempts.get(countryCode);
        
        attempts.removeIf(timestamp -> now - timestamp > timeWindowMillis);
        attempts.add(now);
        
        return attempts.size() >= threshold;
    }
    
    public int getAttemptCount(String countryCode) {
        List<Long> attempts = blockedAttempts.get(countryCode);
        if (attempts == null) {
            return 0;
        }
        
        long now = Instant.now().toEpochMilli();
        attempts.removeIf(timestamp -> now - timestamp > timeWindowMillis);
        
        return attempts.size();
    }
    
    public void cleanup() {
        long now = Instant.now().toEpochMilli();
        blockedAttempts.values().forEach(attempts -> 
            attempts.removeIf(timestamp -> now - timestamp > timeWindowMillis)
        );
        blockedAttempts.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
