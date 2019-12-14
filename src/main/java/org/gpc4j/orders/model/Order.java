package org.gpc4j.orders.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class Order {

  private Integer orderId;
  private Date orderDate;
  private String productId;
  private String productName;
  private BigDecimal Quantity;
  private String unit = "kg";

}
