package edu.campus.canteen.service;

import edu.campus.canteen.dto.DishQueryRequest;
import edu.campus.canteen.dto.DishCreateRequest;
import edu.campus.canteen.dto.DishUpdateRequest;
import edu.campus.canteen.dto.DishStatusRequest;
import edu.campus.canteen.dto.DishStockRequest;
import edu.campus.canteen.dto.DishPriceRequest;
import edu.campus.canteen.vo.DishVO;
import edu.campus.canteen.vo.PageVO;
import java.util.List;

public interface DishService {
    PageVO<DishVO> getPublicDishes(DishQueryRequest query);

    DishVO getPublicDishById(Long id);

    List<DishVO> getHotDishes();

    PageVO<DishVO> getAdminDishes(DishQueryRequest query);

    DishVO getAdminDishById(Long id);

    DishVO createDish(DishCreateRequest request);

    DishVO updateDish(Long id, DishUpdateRequest request);

    DishVO updateDishStatus(Long id, DishStatusRequest request);

    DishVO updateDishStock(Long id, DishStockRequest request);

    DishVO updateDishPrice(Long id, DishPriceRequest request);

    void deleteDish(Long id);
}
