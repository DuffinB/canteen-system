package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.canteen.dto.CartAddRequest;
import edu.campus.canteen.dto.CartQuantityRequest;
import edu.campus.canteen.entity.Cart;
import edu.campus.canteen.entity.Category;
import edu.campus.canteen.entity.Dish;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.CartMapper;
import edu.campus.canteen.mapper.CategoryMapper;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.service.CartService;
import edu.campus.canteen.vo.CartItemVO;
import edu.campus.canteen.vo.CartVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {
    private final CartMapper cartMapper;
    private final DishMapper dishMapper;
    private final CategoryMapper categoryMapper;

    public CartServiceImpl(CartMapper cartMapper, DishMapper dishMapper, CategoryMapper categoryMapper) {
        this.cartMapper = cartMapper;
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public CartVO getCurrentUserCart(Long userId) {
        List<Cart> cartRows = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByAsc(Cart::getId));
        if (cartRows.isEmpty()) {
            return new CartVO(List.of(), BigDecimal.ZERO.setScale(2));
        }

        Map<Long, Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .in(Dish::getId, cartRows.stream().map(Cart::getDishId).distinct().toList()))
                .stream().collect(Collectors.toMap(Dish::getId, Function.identity()));

        BigDecimal total = BigDecimal.ZERO;
        var items = new java.util.ArrayList<CartItemVO>();
        for (Cart row : cartRows) {
            Dish dish = dishes.get(row.getDishId());
            // MyBatis-Plus 逻辑删除会从 selectBatchIds 结果中排除 deleted=1 的菜品。
            if (dish == null) {
                continue;
            }
            BigDecimal subtotal = dish.getPrice().multiply(BigDecimal.valueOf(row.getQuantity()));
            items.add(new CartItemVO(dish.getId(), dish.getName(), dish.getImageUrl(), dish.getPrice(),
                    row.getQuantity(), subtotal));
            total = total.add(subtotal);
        }
        return new CartVO(List.copyOf(items), total);
    }

    @Override
    @Transactional
    public CartVO addItem(Long userId, CartAddRequest request) {
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        // 锁定菜品行，使同一菜品的并发添加串行执行，库存和数量校验在锁内完成。
        Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getId, request.dishId())
                .last("FOR UPDATE"));
        if (dish == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"ON_SALE".equals(dish.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_DISH_STATUS);
        }

        Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getId, dish.getCategoryId())
                .last("FOR UPDATE"));
        if (category == null || !Integer.valueOf(1).equals(category.getStatus())) {
            throw new BusinessException(ErrorCode.CATEGORY_UNAVAILABLE);
        }

        Cart existing = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getDishId, dish.getId())
                .last("FOR UPDATE"));
        long newQuantity = (existing == null ? 0L : existing.getQuantity().longValue()) + request.quantity();
        if (newQuantity > dish.getStock()) {
            throw new BusinessException(ErrorCode.CART_STOCK_INSUFFICIENT);
        }

        if (existing == null) {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setDishId(dish.getId());
            cart.setQuantity(request.quantity());
            try {
                cartMapper.insert(cart);
            } catch (DuplicateKeyException exception) {
                // 兜底处理其他写入路径造成的 (user_id,dish_id) 唯一键冲突，不泄漏 SQL 异常。
                throw new BusinessException(ErrorCode.CART_ITEM_CONFLICT);
            }
        } else {
            existing.setQuantity((int) newQuantity);
            cartMapper.updateById(existing);
        }
        return getCurrentUserCart(userId);
    }

    @Override
    @Transactional
    public CartVO updateItemQuantity(Long userId, Long dishId, CartQuantityRequest request) {
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        // 与添加操作使用相同的加锁顺序，避免库存校验与菜品状态更新并发冲突。
        Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getId, dishId)
                .last("FOR UPDATE"));
        if (dish == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"ON_SALE".equals(dish.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_DISH_STATUS);
        }
        if (request.quantity() > dish.getStock()) {
            throw new BusinessException(ErrorCode.CART_STOCK_INSUFFICIENT);
        }

        Cart cart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getDishId, dishId)
                .last("FOR UPDATE"));
        if (cart == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        cart.setQuantity(request.quantity());
        cartMapper.updateById(cart);
        return getCurrentUserCart(userId);
    }

    @Override
    @Transactional
    public void removeItem(Long userId, Long dishId) {
        Cart cart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getDishId, dishId)
                .last("FOR UPDATE"));
        if (cart == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 删除条件再次带上当前用户ID，确保数据库写操作本身也受用户归属约束。
        cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getDishId, dishId));
    }
}
