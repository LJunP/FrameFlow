package com.frameflow.learning.product.web;

import com.frameflow.learning.product.service.UploadService;
import com.frameflow.learning.product.web.BatchDtos.CompleteUploadRequest;
import com.frameflow.learning.product.web.BatchDtos.CompleteUploadResponse;
import com.frameflow.learning.product.web.BatchDtos.UploadPartsRequest;
import com.frameflow.learning.product.web.BatchDtos.UploadPartsResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 候选上传会话接口：领分片 URL、确认完成。
 */
@RestController
@RequestMapping("/api/v1/candidates")
public class CandidateController {

    private final UploadService uploadService;

    public CandidateController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/{id}/upload-parts")
    public UploadPartsResponse uploadParts(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable long id,
                                           @Valid @RequestBody UploadPartsRequest req) {
        return uploadService.uploadParts(Long.parseLong(jwt.getSubject()), id, req.partNumbers());
    }

    @PostMapping("/{id}/complete")
    public CompleteUploadResponse complete(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable long id,
                                           @RequestBody(required = false) CompleteUploadRequest req) {
        return uploadService.complete(Long.parseLong(jwt.getSubject()), id, req);
    }
}
