package com.marchena.ae.fn;

import com.marchena.ae.fn.cripto.HybridEncryptor;
import com.marchena.ae.fn.cripto.SecretCacheService;
import com.marchena.ae.fn.model.EncryptRequest;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

public class HybridEncryptFunction {

    @FunctionName("hybridEncrypt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = HttpMethod.POST,
                    authLevel = AuthorizationLevel.ANONYMOUS, route = "encrypt")
            HttpRequestMessage<Optional<EncryptRequest>> request,
            final ExecutionContext context) {

        EncryptRequest req = request.getBody().orElse(null);
        if (req == null || req.getMessage() == null || req.getMessage().isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("{'error': 'message field is required'}")
                    .build();
        }

        try {
            var publicKey = SecretCacheService.getInstance()
                    .getPublicKey("kek-json-dev");

            String encrypted = HybridEncryptor.encrypt(
                    req.getMessage().getBytes("UTF-8"), publicKey);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "text/plain")
                    .body(encrypted)
                    .build();

        } catch (Exception e) {
            context.getLogger().severe("Error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Encryption failed")
                    .build();
        }
    }
}
