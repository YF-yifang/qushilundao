package com.qsld.mq;

import cn.hutool.json.JSONUtil;
import org.aspectj.weaver.ast.Or;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author YiFang
 * @date 2023/7/17 22:07
 */
@Component
public class OrderProducer {
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public OrderProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendOrder(Order order) {
        String jsonStr = JSONUtil.toJsonStr(order);
//        rabbitTemplate.convertAndSend("test", "test", jsonStr);
        rabbitTemplate.convertAndSend("test", "test", "939234919515");
    }
}
