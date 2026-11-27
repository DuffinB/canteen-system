package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.AdminReviewQueryRequest;
import edu.campus.canteen.dto.ReviewAuditRequest;
import edu.campus.canteen.security.JwtAuthenticationPrincipal;
import edu.campus.canteen.service.ReviewService;
import edu.campus.canteen.vo.AdminReviewVO;
import edu.campus.canteen.vo.PageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {
    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ApiResponse<PageVO<AdminReviewVO>> list(@Valid @ModelAttribute AdminReviewQueryRequest query) {
        return ApiResponse.success(reviewService.getAdminReviews(query));
    }

    @PutMapping("/{id}/audit")
    public ApiResponse<Void> audit(Authentication authentication, @PathVariable @Positive Long id,
                                   @Valid @RequestBody ReviewAuditRequest request) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        reviewService.audit(principal.userId(), id, request);
        return ApiResponse.success(null);
    }
}
