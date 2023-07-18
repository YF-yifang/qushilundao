package com.qsld.utils;

import com.qsld.entity.VoucherOrder;
import com.qsld.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author YiFang
 * @date 2023/7/18 18:22
 */
@Slf4j
@Component
public class MQMessageHandler {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = "orderQueue")
    public void handlerOrderMessage(VoucherOrder voucherOrder){
        try {
            //获取用户
            Long userId = voucherOrder.getUserId();

            //创建锁对象
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            //获取锁
            boolean isLock = lock.tryLock();
            //判断是否获取锁成功
            if (!isLock) {
                //获取锁失败
                log.error("不允许重复下单！");
                return;
            }
            try {
                //8.返回订单id
                voucherOrderService.createVoucherOrder(voucherOrder);
            } finally {
                //释放锁
                lock.unlock();
            }
        } catch (Exception e){
            log.error("处理订单异常：" + e);
        }
    }
}
