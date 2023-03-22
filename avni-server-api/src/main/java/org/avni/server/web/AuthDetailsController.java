package org.avni.server.web;

import org.avni.server.config.AvniKeycloakConfig;
import org.avni.server.config.CognitoConfig;
import org.avni.server.config.IdpType;
import org.keycloak.OAuth2Constants;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthDetailsController {
    private final AvniKeycloakConfig avniKeycloakConfig;
    private final AdapterConfig adapterConfig;
    private final CognitoConfig cognitoConfig;

    @Value("${avni.idp.type}")
    private IdpType idpType;

    @Autowired
    public AuthDetailsController(AvniKeycloakConfig avniKeycloakConfig, AdapterConfig adapterConfig, CognitoConfig cognitoConfig) {
        this.avniKeycloakConfig = avniKeycloakConfig;
        this.adapterConfig = adapterConfig;
        this.cognitoConfig = cognitoConfig;
    }

    @RequestMapping(value = "/cognito-details", method = RequestMethod.GET)
    public CompositeIDPDetails.Cognito getAuthDetails() {
        return new CompositeIDPDetails.Cognito(cognitoConfig.getPoolId(), cognitoConfig.getClientId());
    }

    @RequestMapping(value = "/idp-details", method = RequestMethod.GET)
    public CompositeIDPDetails getCompositeIDPDetails() {
        String keycloakGrantType = OAuth2Constants.PASSWORD;
        String keycloakScope = "openid";
        String keycloakClientId = avniKeycloakConfig.getVerifyTokenAudience();
        String keycloakAuthServerUrl = adapterConfig.getAuthServerUrl();
        String cognitoConfigPoolId = cognitoConfig.getPoolId();
        String cognitoConfigClientId = cognitoConfig.getClientId();
        return new AuthDetailsController.CompositeIDPDetails(keycloakAuthServerUrl, keycloakClientId,
                keycloakGrantType, keycloakScope, cognitoConfigPoolId, cognitoConfigClientId, idpType);
    }

    public static class CompositeIDPDetails {
        private final IdpType idpType;
        private final Keycloak keycloak;
        private final Cognito cognito;

        public CompositeIDPDetails(String authServerUrl, String keycloakClientId, String grantType, String scope,
                                   String poolId, String clientId, IdpType idpType) {
            this.idpType = idpType;
            this.keycloak = new Keycloak(authServerUrl, keycloakClientId, grantType, scope);
            this.cognito = new Cognito(poolId, clientId);
        }

        public Keycloak getKeycloak() {
            return keycloak;
        }

        public Cognito getCognito() {
            return cognito;
        }

        public class Keycloak {
            private final String authServerUrl;
            private final String clientId;
            private final String grantType;
            private final String scope;

            public Keycloak(String authServerUrl, String clientId, String grantType, String scope) {
                this.authServerUrl = authServerUrl;
                this.clientId = clientId;
                this.grantType = grantType;
                this.scope = scope;
            }

            public String getAuthServerUrl() {
                return authServerUrl;
            }

            public String getClientId() {
                return clientId;
            }

            public String getGrantType() {
                return grantType;
            }

            public String getScope() {
                return scope;
            }
        }

        public static class Cognito {
            private final String poolId;
            private final String clientId;

            public Cognito(String poolId, String clientId) {
                this.poolId = poolId;
                this.clientId = clientId;
            }

            public String getPoolId() {
                return poolId;
            }

            public String getClientId() {
                return clientId;
            }
        }
    }
}
