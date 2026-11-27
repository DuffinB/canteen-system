package edu.campus.canteen.controller;

import edu.campus.canteen.common.ApiResponse;
import edu.campus.canteen.dto.ReviewCreateRequest;
import edu.campus.canteen.dto.ReviewPageRequest;
import edu.campus.canteen.dto.ReviewUpdateRequest;
import edu.campus.canteen.security.JwtAuthenticationPrincipal;
import edu.campus.canteen.service.ReviewService;
import edu.campus.canteen.vo.DishReviewVO;
import edu.campus.canteen.vo.PageVO;
import edu.campus.canteen.vo.ReviewVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ApiResponse<ReviewVO> create(Authentication authentication,
                                        @Valid @RequestBody ReviewCreateRequest request) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(reviewService.create(principal.userId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ReviewVO> update(Authentication authentication, @PathVariable @Positive Long id,
                                        @Valid @RequestBody ReviewUpdateRequest request) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(reviewService.update(principal.userId(), id, request));
    }

    @GetMapping("/my")
    public ApiResponse<PageVO<ReviewVO>> myReviews(Authentication authentication,
                                                   @Valid @ModelAttribute ReviewPageRequest query) {
        JwtAuthenticationPrincipal principal = (JwtAuthenticationPrincipal) authentication.getPrincipal();
        return ApiResponse.success(reviewService.getMyReviews(principal.userId(), query));
    }

    @GetMapping("/dish/{dishId}")
    public ApiResponse<PageVO<DishReviewVO>> dishReviews(@PathVariable @Positive Long dishId,
                                                         @Valid @ModelAttribute ReviewPageRequest query) {
        return ApiResponse.success(reviewService.getApprovedDishReviews(dishId, query));
    }
}
