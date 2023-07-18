package com.qsld.mq;

import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author YiFang
 * @date 2023/7/17 22:09
 */
@Component
public class OrderConsumer {

    private Logger logger;

    @RabbitListener(queues = "testqueue")
    public void receiveOrder(String msg) {
//        System.out.println("1111111");
        System.out.println("未序列化 = " + msg);
//        Order order = JSONUtil.toBean(msg, Order.class);
        // 处理订单消息
//        System.out.println("序列化后 =  " + order);
//        logger.info("序列化后：{}", order);
        // ...
    }
}
