package dev.jagadeesh.booking.tenant;

import dev.jagadeesh.booking.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    public record NewTenant(@NotBlank String name, @NotBlank String timezone, @Min(0) @Max(10080) int cancelCutoffMinutes,
                            @Min(1) @Max(60) int holdMinutes, @Email @NotBlank String adminEmail, @NotBlank String adminName,
                            @Size(min = 8, max = 72) String adminPassword) {
    }

    public record NewUser(@Email @NotBlank String email, @NotBlank String fullName, @Size(min = 8, max = 72) String password) {
    }

    public record NewStaff(@Email @NotBlank String email, @NotBlank String fullName, @Size(min = 8, max = 72) String password,
                           @NotNull CurrentUser.Role role) {
    }

    public record Login(@NotNull UUID tenantId, @NotBlank String email, @NotBlank String password) {
    }

    public record Created(UUID id) {
    }

    @PostMapping("/api/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public Created createTenant(@Valid @RequestBody NewTenant r) {
        return new Created(accounts.createTenant(r.name(), r.timezone(), r.cancelCutoffMinutes(), r.holdMinutes(),
                r.adminEmail(), r.adminName(), r.adminPassword()).getId());
    }

    @PostMapping("/api/tenants/{tenantId}/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Created registerCustomer(@PathVariable UUID tenantId, @Valid @RequestBody NewUser r) {
        return new Created(accounts.addUser(tenantId, r.email(), r.fullName(), r.password(), CurrentUser.Role.CUSTOMER).getId());
    }

    @PostMapping("/api/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public Created addStaff(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewStaff r) {
        CurrentUser me = CurrentUser.of(jwt);
        me.requireAdmin();
        return new Created(accounts.addUser(me.tenantId(), r.email(), r.fullName(), r.password(), r.role()).getId());
    }

    @PostMapping("/api/auth/login")
    public AccountService.Token login(@Valid @RequestBody Login r) {
        return accounts.login(r.tenantId(), r.email(), r.password());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badLogin(BadCredentialsException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        p.setProperty("code", "BAD_CREDENTIALS");
        return p;
    }
}
