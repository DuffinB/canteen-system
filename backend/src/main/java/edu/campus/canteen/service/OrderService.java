package edu.campus.canteen.service;

import edu.campus.canteen.dto.OrderCreateRequest;
import edu.campus.canteen.dto.OrderQueryRequest;
import edu.campus.canteen.dto.AdminOrderQueryRequest;
import edu.campus.canteen.vo.PageVO;
import edu.campus.canteen.vo.OrderVO;
import edu.campus.canteen.vo.AdminOrderDetailVO;
import edu.campus.canteen.vo.AdminOrderSummaryVO;

public interface OrderService {
    OrderVO submitOrder(Long userId, String idempotencyKey, OrderCreateRequest request);

    PageVO<OrderVO> getMyOrders(Long userId, OrderQueryRequest query);

    OrderVO getMyOrder(Long userId, Long orderId);

    void cancelMyOrder(Long userId, Long orderId);

    PageVO<AdminOrderSummaryVO> getAdminOrders(AdminOrderQueryRequest query);

    AdminOrderDetailVO getAdminOrder(Long orderId);

    void confirmAdminOrder(Long adminId, Long orderId);

    void markAdminOrderReady(Long adminId, Long orderId);

    void completeAdminOrder(Long adminId, Long orderId);

    void cancelAdminOrder(Long adminId, Long orderId);

    void confirmPickup(Long userId, Long orderId);
}
