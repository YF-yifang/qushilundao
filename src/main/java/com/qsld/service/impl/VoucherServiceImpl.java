package com.qsld.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qsld.dto.Result;
import com.qsld.entity.Voucher;
import com.qsld.mapper.VoucherMapper;
import com.qsld.entity.SeckillVoucher;
import com.qsld.service.ISeckillVoucherService;
import com.qsld.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qsld.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // 保存秒杀库存到redis
        //stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
        Map<String, String> map = new HashMap<>();
        map.put("stock", voucher.getStock().toString());
        map.put("beginTime", String.valueOf(voucher.getBeginTime().toEpochSecond(ZoneOffset.of("+8"))));
        map.put("endTime", String.valueOf(voucher.getEndTime().toEpochSecond(ZoneOffset.of("+8"))));
        stringRedisTemplate.opsForHash().putAll(SECKILL_STOCK_KEY + voucher.getId(), map);
    }
}
