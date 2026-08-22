package com.frameflow.learning.product.web;

import java.util.List;

import com.frameflow.learning.product.service.QualityProfileService;
import com.frameflow.learning.product.web.ProductDtos.CreateProfileRequest;
import com.frameflow.learning.product.web.ProductDtos.ProfileResponse;
import com.frameflow.learning.product.web.ProductDtos.ProfileVersionResponse;
import com.frameflow.learning.product.web.ProductDtos.PublishVersionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quality Profile 接口：团队级质检标准的创建与版本发布。
 * 版本一经发布不可修改（物理层由 Mapper 无 UPDATE 保证，见 QualityProfileMapper）。
 */
@RestController
@RequestMapping("/api/v1/quality-profiles")
public class QualityProfileController {

    private final QualityProfileService service;

    public QualityProfileController(QualityProfileService service) {
        this.service = service;
    }

    private static long teamId(Jwt jwt) {
        return jwt.getClaim("tid");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody CreateProfileRequest req) {
        return service.create(Long.parseLong(jwt.getSubject()), teamId(jwt), req);
    }

    @GetMapping
    public List<ProfileResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(Long.parseLong(jwt.getSubject()), teamId(jwt));
    }

    @GetMapping("/{id}/versions")
    public List<ProfileVersionResponse> listVersions(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable long id) {
        return service.listVersions(Long.parseLong(jwt.getSubject()), id);
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileVersionResponse publishVersion(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable long id,
                                                 @Valid @RequestBody PublishVersionRequest req) {
        return service.publishVersion(Long.parseLong(jwt.getSubject()), id, req);
    }

    @GetMapping("/{id}/versions/{versionNo}")
    public ProfileVersionResponse getVersion(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable long id,
                                             @PathVariable int versionNo) {
        return service.getVersion(Long.parseLong(jwt.getSubject()), id, versionNo);
    }
}
