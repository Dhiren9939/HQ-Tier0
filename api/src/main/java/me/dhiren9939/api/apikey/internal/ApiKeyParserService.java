package me.dhiren9939.api.apikey.internal;

import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.ApiKeyParsingFailure;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a presented access identifier and extracts its key format version.
 */
@Slf4j
@Service
class ApiKeyParserService {

    private static final Pattern API_KEY_PATTERN = Pattern.compile("^(V\\d+)_.+$");

    ApiKey parse(String accessId, String accessKey){
        Matcher matcher = API_KEY_PATTERN.matcher(accessId);
        if(!matcher.matches())
            throw new ApiKeyParsingFailure("Invalid ApiKey format");

        String version = matcher.group(1);

        return new ApiKey(accessId, accessKey, version);
    }
}
