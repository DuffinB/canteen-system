package edu.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.campus.canteen.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
