package com.qsld.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.qsld.dto.Result;
import com.qsld.dto.UserDTO;
import com.qsld.entity.SeckillVoucher;
import com.qsld.entity.VoucherOrder;
import com.qsld.mapper.VoucherOrderMapper;
import com.qsld.service.ISeckillVoucherService;
import com.qsld.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qsld.utils.RedisIdWorker;
import com.qsld.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /*@PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }*/

/*    //代理对象
    private IVoucherOrderService proxy;

    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();*/

    // 使用RabbitMQ改造消息队列
    @Override
    public Result seckillVoucher(Long voucherId) {
        //订单id
        long orderId = redisIdWorker.nextId("order");
        //获取用户
        UserDTO user = UserHolder.getUser();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                user.getId().toString(),
                String.valueOf(orderId),
                String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8")))
        );
        assert result != null;
        //2.判断结果是否为0
        if (result != 0) {
            //2.1.获取当前日期，精确到天
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            //2.2.自增长-1
            stringRedisTemplate.opsForValue().increment("icr:order:" + date, -1);
            //2.1.不为0，代表没有购买资格，返回错误信息
            return Result.fail(
                    result == 1 ? "秒杀还未开始！" :
                            result == 2 ? "秒杀已结束！" :
                                    result == 3 ? "库存不足" : "不允许重复下单");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(user.getId());
        voucherOrder.setVoucherId(voucherId);
        //将订单信息发送给RabbitMQ
        rabbitTemplate.convertAndSend("orderExchange", "order", voucherOrder);
        //3.返回订单id
        return Result.ok(orderId);
    }

    //阻塞队列
    /*private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<VoucherOrder>(1024 * 1024);
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常：", e);
                }
            }
        }
    }*/

    //Redis的Stream队列
    /*private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        //2.1.如果失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    //3.解析订单中的信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //4.如果成功，创建订单
                    handlerVoucherOrder(voucherOrder);
                    //5.ack确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常：", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    //1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2.判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        //2.1.如果失败，说明pending-list没有异常消息，结束循环
                        break;
                    }
                    //3.解析订单中的信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //4.如果成功，创建订单
                    handlerVoucherOrder(voucherOrder);
                    //5.ack确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list订单异常：", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //订单id
        long orderId = redisIdWorker.nextId("order");
        //获取用户
        UserDTO user = UserHolder.getUser();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                user.getId().toString(),
                String.valueOf(orderId),
                String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8")))
        );
        assert result != null;
        //2.判断结果是否为0
        if (result != 0) {
            //2.1.获取当前日期，精确到天
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            //2.2.自增长-1
            stringRedisTemplate.opsForValue().increment("icr:order:" + date, -1);
            //2.1.不为0，代表没有购买资格，返回错误信息
            return Result.fail(
                    result == 1 ? "秒杀还未开始！" :
                    result == 2 ? "秒杀已结束！" :
                    result == 3 ? "库存不足" : "不允许重复下单");
        }

        //获取代理对象（事务）
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3.返回订单id
        return Result.ok(orderId);
    }*/

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        UserDTO user = UserHolder.getUser();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), user.getId().toString()
        );
        assert result != null;
        //2.判断结果是否为0
        if (result != 0) {
            //2.1.不为0，代表没有购买资格
            return Result.fail(result == 1 ? "库存不足！" : "不允许重复下单！");
        }
        //2.2.为0，有购买资格，把下单信息保存到阻塞队列
        Long userId = UserHolder.getUser().getId();
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        //2.3.放入阻塞队列
        orderTasks.add(voucherOrder);
        //获取代理对象（事务）
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3.返回订单id
        return Result.ok(orderId);
    }*/

    /*private void handlerVoucherOrder(VoucherOrder voucherOrder) {
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
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }*/

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        //3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //已经结束
            return Result.fail("秒杀已经结束!");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
            //获取锁失败
            return Result.fail("不允许重复下单！");
        }
        try {
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //8.返回订单id
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
    }*/

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.一人一单
        Long userId = voucherOrder.getUserId();
        //5.1.查询订单
        Long count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId()).count();
        //5.2.判断是否存在
        if (count > 0) {
            //用户已经购买过，不允许再次下单
            log.error("该用户已经购买过一次！");
            return;
        }
        //6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            //扣减失败
            log.error("库存不足！");
            return;
        }
        save(voucherOrder);
    }
}
