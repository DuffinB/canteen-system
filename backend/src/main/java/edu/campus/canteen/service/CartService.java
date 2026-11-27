package edu.campus.canteen.service;

import edu.campus.canteen.dto.CartAddRequest;
import edu.campus.canteen.dto.CartQuantityRequest;
import edu.campus.canteen.vo.CartVO;

public interface CartService {
    CartVO getCurrentUserCart(Long userId);

    CartVO addItem(Long userId, CartAddRequest request);

    CartVO updateItemQuantity(Long userId, Long dishId, CartQuantityRequest request);

    void removeItem(Long userId, Long dishId);
}
