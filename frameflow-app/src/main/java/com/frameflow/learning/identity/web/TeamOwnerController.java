package com.frameflow.learning.identity.web;

import com.frameflow.learning.identity.service.AuthService;
import com.frameflow.learning.identity.web.AuthDtos.TransferOwnerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamOwnerController {

    private final AuthService authService;

    public TeamOwnerController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/v1/teams/{teamId}/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable long teamId,
                         @Valid @RequestBody TransferOwnerRequest req) {
        authService.transferOwnership(Long.parseLong(jwt.getSubject()), teamId, req.userId());
    }
}
