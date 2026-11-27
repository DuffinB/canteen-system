package edu.campus.canteen.service;

import edu.campus.canteen.dto.AdminReviewQueryRequest;
import edu.campus.canteen.dto.ReviewAuditRequest;
import edu.campus.canteen.dto.ReviewCreateRequest;
import edu.campus.canteen.dto.ReviewUpdateRequest;
import edu.campus.canteen.dto.ReviewPageRequest;
import edu.campus.canteen.vo.AdminReviewVO;
import edu.campus.canteen.vo.DishReviewVO;
import edu.campus.canteen.vo.PageVO;
import edu.campus.canteen.vo.ReviewVO;

public interface ReviewService {
    ReviewVO create(Long userId, ReviewCreateRequest request);

    ReviewVO update(Long userId, Long reviewId, ReviewUpdateRequest request);

    PageVO<ReviewVO> getMyReviews(Long userId, ReviewPageRequest query);

    PageVO<DishReviewVO> getApprovedDishReviews(Long dishId, ReviewPageRequest query);

    PageVO<AdminReviewVO> getAdminReviews(AdminReviewQueryRequest query);

    void audit(Long adminId, Long reviewId, ReviewAuditRequest request);
}
