package dev.jagadeesh.booking.tenant;

import dev.jagadeesh.booking.common.CurrentUser;
import dev.jagadeesh.booking.common.NotFoundException;
import dev.jagadeesh.booking.common.RuleViolation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountService {

    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwt;
    private final Duration ttl;

    public AccountService(TenantRepository tenants, AppUserRepository users, PasswordEncoder encoder, JwtEncoder jwt,
                          @Value("${booking.jwt.ttl:1h}") Duration ttl) {
        this.tenants = tenants;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.ttl = ttl;
    }

    public record Token(String accessToken, long expiresIn, String role, UUID tenantId) {
    }

    /** Sign-up for a new business: creates the tenant and its first admin together. */
    @Transactional
    public Tenant createTenant(String name, String timezone, int cancelCutoffMinutes, int holdMinutes, String adminEmail, String adminName, String password) {
        Tenant t = tenants.save(new Tenant(name, timezone, cancelCutoffMinutes, holdMinutes));
        users.save(new AppUser(t.getId(), adminEmail, adminName, encoder.encode(password), CurrentUser.Role.ADMIN));
        return t;
    }

    /** Customers sign up themselves; staff are added by the admin. */
    @Transactional
    public AppUser addUser(UUID tenantId, String email, String name, String password, CurrentUser.Role role) {
        tenant(tenantId);
        if (users.existsByTenantIdAndEmail(tenantId, email.toLowerCase())) {
            throw new RuleViolation("EMAIL_TAKEN", "This email is already registered with this business");
        }
        return users.save(new AppUser(tenantId, email, name, encoder.encode(password), role));
    }

    @Transactional(readOnly = true)
    public Token login(UUID tenantId, String email, String password) {
        AppUser u = users.findByTenantIdAndEmail(tenantId, email.toLowerCase())
                .filter(x -> encoder.matches(password, x.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Wrong email or password"));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("booking").subject(u.getId().toString()).issuedAt(now).expiresAt(now.plus(ttl))
                .claim("tenant", u.getTenantId().toString())
                .claim("role", u.getRole().name())
                .claim("roles", java.util.List.of(u.getRole().name()))
                .build();
        String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Token(token, ttl.toSeconds(), u.getRole().name(), u.getTenantId());
    }

    public Tenant tenant(UUID id) {
        return tenants.findById(id).orElseThrow(() -> new NotFoundException("Business " + id));
    }
}
