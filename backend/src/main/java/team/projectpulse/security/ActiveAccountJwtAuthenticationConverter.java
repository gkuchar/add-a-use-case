package team.projectpulse.security;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

/**
 * Refuses a bearer token whose user has been deactivated or no longer exists (FR-SEC-active-account), so that
 * deactivation takes effect at the user's next request rather than when her token expires.
 *
 * <p>Wraps the converter that maps the token's claims to authorities, then reads the {@code userId} claim's user: one
 * primary-key read per bearer request. A {@link DisabledException} reaches {@link CustomBearerTokenAuthenticationEntryPoint},
 * which hands it to {@code ExceptionHandlerAdvice}'s {@code AccountStatusException} handler for the {@code 401} envelope.
 */
public class ActiveAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> delegate;

    private final UserRepository userRepository;


    public ActiveAccountJwtAuthenticationConverter(Converter<Jwt, ? extends AbstractAuthenticationToken> delegate, UserRepository userRepository) {
        this.delegate = delegate;
        this.userRepository = userRepository;
    }

    @Override
    public @Nullable AbstractAuthenticationToken convert(Jwt jwt) {
        AbstractAuthenticationToken authentication = this.delegate.convert(jwt);
        Object userIdClaim = jwt.getClaims().get("userId");
        if (!(userIdClaim instanceof Number userId)) {
            throw new DisabledException("The token names no user.");
        }
        boolean active = this.userRepository.findById(userId.intValue())
                .map(PeerEvaluationUser::isEnabled)
                .orElse(false);
        if (!active) {
            throw new DisabledException("The account is deactivated or no longer exists.");
        }
        return authentication;
    }

}
