package com.frameflow.learning.product.web;

import com.frameflow.learning.product.service.RankingService;
import com.frameflow.learning.product.service.RankingService.RankSummary;
import com.frameflow.learning.product.web.RankingDtos.LatestRankingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 排名接口：触发排名（生成不可变快照）与查看最新快照。 */
@RestController
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @PostMapping("/api/v1/batches/{id}/rank")
    @ResponseStatus(HttpStatus.CREATED)
    public RankSummary rank(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return rankingService.rank(Long.parseLong(jwt.getSubject()), id);
    }

    @GetMapping("/api/v1/batches/{id}/ranking/latest")
    public LatestRankingResponse latest(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return rankingService.latestView(Long.parseLong(jwt.getSubject()), id);
    }
}
