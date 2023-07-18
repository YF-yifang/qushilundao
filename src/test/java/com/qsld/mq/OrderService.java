package com.qsld.mq;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author YiFang
 * @date 2023/7/17 22:10
 */
@Service
public class OrderService {
    private final OrderProducer orderProducer;

    @Autowired
    public OrderService(OrderProducer orderProducer) {
        this.orderProducer = orderProducer;
    }

    public void placeOrder(Order order) {
        // 处理用户下单逻辑
        // ...
        // 发送订单消息到RabbitMQ
        orderProducer.sendOrder(order);
    }
}
