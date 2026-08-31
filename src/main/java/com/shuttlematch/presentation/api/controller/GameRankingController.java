package com.shuttlematch.presentation.api.controller;

import com.shuttlematch.application.TooManyRequestsException;
import com.shuttlematch.application.usecase.game.GetRankingUseCase;
import com.shuttlematch.application.usecase.game.SubmitScoreResult;
import com.shuttlematch.application.usecase.game.SubmitScoreUseCase;
import com.shuttlematch.domain.model.game.MiniGame;
import com.shuttlematch.domain.model.game.PlayerName;
import com.shuttlematch.infrastructure.security.ScoreSubmissionRateLimiter;
import com.shuttlematch.presentation.api.request.SubmitScoreRequest;
import com.shuttlematch.presentation.api.response.RankingResponse;
import com.shuttlematch.presentation.api.response.SubmitScoreResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ミニゲーム(/game)のランキングを扱う REST コントローラ。
 *
 * <p>認証は無く、誰でも取得・登録できる。荒らし対策は「上位5件しか残さない」
 * 「スコアの上限で弾く(ドメイン側)」「送信元ごとのレート制限」の3点だけの割り切り。
 */
@RestController
@RequestMapping("/api/v1/games/{game}/ranking")
public class GameRankingController {

    private final GetRankingUseCase getRankingUseCase;
    private final SubmitScoreUseCase submitScoreUseCase;
    private final ScoreSubmissionRateLimiter rateLimiter;

    public GameRankingController(
            GetRankingUseCase getRankingUseCase,
            SubmitScoreUseCase submitScoreUseCase,
            ScoreSubmissionRateLimiter rateLimiter) {
        this.getRankingUseCase = getRankingUseCase;
        this.submitScoreUseCase = submitScoreUseCase;
        this.rateLimiter = rateLimiter;
    }

    /** 上位5件を取得する。ゲームのページとゲームオーバー画面で表示する。 */
    @GetMapping
    public RankingResponse ranking(@PathVariable String game) {
        return RankingResponse.from(getRankingUseCase.execute(MiniGame.fromSlug(game)));
    }

    /**
     * スコアを登録する。ランクインしなければ保存されず、その旨をレスポンスで返す。
     * 表示中のランキングは他の端末の登録で変わるため、ランクインの最終判定はサーバー側で行う。
     */
    @PostMapping
    public SubmitScoreResponse submit(
            @PathVariable String game,
            @Valid @RequestBody SubmitScoreRequest request,
            HttpServletRequest httpRequest) {
        MiniGame miniGame = MiniGame.fromSlug(game);
        if (!rateLimiter.tryAcquire(clientKey(httpRequest))) {
            throw new TooManyRequestsException("登録の間隔が短すぎます。少し待ってからお試しください");
        }
        SubmitScoreResult result = submitScoreUseCase.execute(
                miniGame, PlayerName.of(request.playerName()), request.score());
        return SubmitScoreResponse.from(result);
    }

    /**
     * 送信元の識別子。Cloudflare 経由なら CF-Connecting-IP(Cloudflare が上書きするので
     * クライアントが偽装できない)を優先し、無ければ CloudFront 由来の X-Forwarded-For の
     * 先頭(元のクライアント)、それも無ければ直結のリモートアドレスを使う。
     * いずれにせよレート制限自体が軽い歯止めなので、完全な偽装耐性までは求めない。
     */
    private String clientKey(HttpServletRequest request) {
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
