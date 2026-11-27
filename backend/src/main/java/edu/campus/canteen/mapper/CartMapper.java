package edu.campus.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.campus.canteen.entity.Cart;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartMapper extends BaseMapper<Cart> {
}
