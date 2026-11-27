package edu.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.campus.canteen.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
    @Update("UPDATE reviews SET rating = #{rating}, content = #{content}, status = 'PENDING', "
            + "reviewed_by = NULL, reviewed_at = NULL "
            + "WHERE id = #{reviewId} AND user_id = #{userId}")
    int updateOwnReview(@Param("reviewId") Long reviewId, @Param("userId") Long userId,
                        @Param("rating") Integer rating, @Param("content") String content);

    @Update("UPDATE reviews SET status = #{status}, reviewed_by = #{adminId}, "
            + "reviewed_at = CURRENT_TIMESTAMP WHERE id = #{reviewId}")
    int auditReview(@Param("reviewId") Long reviewId, @Param("adminId") Long adminId,
                    @Param("status") String status);
}
