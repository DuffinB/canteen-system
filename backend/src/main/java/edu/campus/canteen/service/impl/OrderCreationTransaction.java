package edu.campus.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.campus.canteen.dto.OrderCreateRequest;
import edu.campus.canteen.entity.Cart;
import edu.campus.canteen.entity.Category;
import edu.campus.canteen.entity.Dish;
import edu.campus.canteen.entity.Order;
import edu.campus.canteen.entity.OrderItem;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.CartMapper;
import edu.campus.canteen.mapper.CategoryMapper;
import edu.campus.canteen.mapper.DishMapper;
import edu.campus.canteen.mapper.OrderItemMapper;
import edu.campus.canteen.mapper.OrderMapper;
import edu.campus.canteen.vo.OrderItemVO;
import edu.campus.canteen.vo.OrderVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/** 下单写入集中在单个事务中；独立 Bean 便于外层处理唯一键并发冲突。 */
@Service
public class OrderCreationTransaction {
    private final CartMapper cartMapper;
    private final DishMapper dishMapper;
    private final CategoryMapper categoryMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public OrderCreationTransaction(CartMapper cartMapper, DishMapper dishMapper,
                                    CategoryMapper categoryMapper, OrderMapper orderMapper,
                                    OrderItemMapper orderItemMapper) {
        this.cartMapper = cartMapper;
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Transactional
    public OrderVO create(Long userId, String idempotencyKey, OrderCreateRequest request) {
        // 先占用唯一幂等键。若随后任何校验或写入失败，事务回滚会一并撤销该订单行。
        Order order = new Order();
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setTotalAmount(BigDecimal.ZERO.setScale(2));
        order.setStatus("PENDING");
        order.setPickupDate(request.pickupDate());
        order.setPickupSlot(request.pickupSlot());
        order.setRemark(request.remark());
        order.setIdempotencyKey(idempotencyKey);
        orderMapper.insert(order);

        List<Cart> initialCart = cartMapper.selectList(cartRowsForUser(userId));
        if (initialCart.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        List<Long> dishIds = initialCart.stream().map(Cart::getDishId).distinct().sorted().toList();
        Map<Long, Dish> dishesById = lockAndValidateDishes(dishIds);
        validateAndLockCategories(dishIds, dishesById);

        // 重新锁定并读取购物车，避免下单期间加入、删除条目造成漏清或错清。
        List<Cart> cartRows = cartMapper.selectList(cartRowsForUser(userId).last("FOR UPDATE"));
        List<Long> initialCartIds = initialCart.stream().map(Cart::getId).sorted().toList();
        List<Long> currentCartIds = cartRows.stream().map(Cart::getId).sorted().toList();
        if (!initialCartIds.equals(currentCartIds)) {
            throw new BusinessException(ErrorCode.ORDER_CART_CHANGED);
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Cart cart : cartRows) {
            Dish dish = dishesById.get(cart.getDishId());
            if (cart.getQuantity() == null || cart.getQuantity() <= 0
                    || cart.getQuantity() > dish.getStock()) {
                throw new BusinessException(ErrorCode.ORDER_STOCK_INSUFFICIENT);
            }
            int updated = dishMapper.deductStock(dish.getId(), cart.getQuantity());
            if (updated != 1) {
                throw new BusinessException(ErrorCode.ORDER_STOCK_INSUFFICIENT);
            }

            BigDecimal subtotal = dish.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity()));
            totalAmount = totalAmount.add(subtotal);
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setDishId(dish.getId());
            item.setDishNameSnapshot(dish.getName());
            item.setUnitPrice(dish.getPrice());
            item.setQuantity(cart.getQuantity());
            item.setSubtotal(subtotal);
            orderItems.add(item);
        }

        order.setTotalAmount(totalAmount);
        orderMapper.updateById(order);
        for (OrderItem item : orderItems) {
            orderItemMapper.insert(item);
        }

        int deletedCartRows = cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .in(Cart::getId, currentCartIds));
        if (deletedCartRows != currentCartIds.size()) {
            throw new BusinessException(ErrorCode.ORDER_CART_CHANGED);
        }

        Order savedOrder = orderMapper.selectById(order.getId());
        List<OrderItemVO> itemVOs = orderItems.stream()
                .map(item -> new OrderItemVO(item.getDishId(), item.getDishNameSnapshot(),
                        item.getUnitPrice(), item.getQuantity(), item.getSubtotal()))
                .toList();
        return new OrderVO(savedOrder.getId(), savedOrder.getOrderNo(), savedOrder.getTotalAmount(),
                savedOrder.getStatus(), savedOrder.getPickupDate(), savedOrder.getPickupSlot(),
                savedOrder.getRemark(), itemVOs, savedOrder.getCreatedAt());
    }

    private Map<Long, Dish> lockAndValidateDishes(List<Long> dishIds) {
        Map<Long, Dish> dishesById = new LinkedHashMap<>();
        for (Long dishId : dishIds) {
            Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                    .eq(Dish::getId, dishId)
                    .last("FOR UPDATE"));
            if (dish == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (!"ON_SALE".equals(dish.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_DISH_STATUS);
            }
            dishesById.put(dishId, dish);
        }
        return dishesById;
    }

    private void validateAndLockCategories(List<Long> dishIds, Map<Long, Dish> dishesById) {
        TreeSet<Long> categoryIds = new TreeSet<>();
        dishIds.forEach(dishId -> categoryIds.add(dishesById.get(dishId).getCategoryId()));
        for (Long categoryId : categoryIds) {
            Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getId, categoryId)
                    .last("FOR UPDATE"));
            if (category == null || !Integer.valueOf(1).equals(category.getStatus())) {
                throw new BusinessException(ErrorCode.CATEGORY_UNAVAILABLE);
            }
        }
    }

    private LambdaQueryWrapper<Cart> cartRowsForUser(Long userId) {
        return new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByAsc(Cart::getDishId);
    }
}
