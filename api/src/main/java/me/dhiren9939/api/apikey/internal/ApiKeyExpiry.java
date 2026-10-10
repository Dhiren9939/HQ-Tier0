package me.dhiren9939.api.apikey.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ApiKeyExpiryService {
    public enum Duration {
        ONE_DAY,
        ONE_MONTH,
        NEVER
    }

    
}
