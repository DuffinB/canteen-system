package edu.campus.canteen.mapper;

public class DishSalesSummaryRow {
    private Long dishId;
    private String dishName;
    private Long salesQuantity;

    public Long getDishId() { return dishId; }
    public void setDishId(Long dishId) { this.dishId = dishId; }
    public String getDishName() { return dishName; }
    public void setDishName(String dishName) { this.dishName = dishName; }
    public Long getSalesQuantity() { return salesQuantity; }
    public void setSalesQuantity(Long salesQuantity) { this.salesQuantity = salesQuantity; }
}
