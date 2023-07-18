package com.qsld.mq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author YiFang
 * @date 2023/7/17 22:28
 */
@SpringBootTest
public class RabbitmqApplicationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderProducer orderProducer;

    @Autowired
    private OrderConsumer orderConsumer;

    @Test
    void testSend() {
        Order order = new Order();
        order.setName("小米");
        order.setDescription("战斗机中的战斗机");
//        orderService.placeOrder(order);
        orderProducer.sendOrder(order);
    }

}
