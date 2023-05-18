package com.lsdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.lsdp.dto.Result;
import com.lsdp.entity.ShopType;
import com.lsdp.mapper.ShopTypeMapper;
import com.lsdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.lsdp.utils.RedisConstants.TYPE_SHOP_KEY;
import static com.lsdp.utils.RedisConstants.TYPE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        //1.从redis查询商铺类型缓存
        //String shopTypeJson = stringRedisTemplate.opsForValue().get(TYPE_SHOP_KEY);
        List<String> shopTypeListJson = stringRedisTemplate.opsForList().range(TYPE_SHOP_KEY, 0, TYPE_SHOP_TTL);
        //2.判断缓存是否击中
        assert shopTypeListJson != null;
        if (!shopTypeListJson.isEmpty()) {
            //3.击中，直接返回
            List<ShopType> shopTypes = new ArrayList<>();
            for (String type : shopTypeListJson) {
                shopTypes.add(JSONUtil.toBean(type, ShopType.class));
            }
            return Result.ok(shopTypes);
        }
        //4.未击中，从数据库中查询商铺类型
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //5.判断数据库中是否存在
        if (shopTypes.isEmpty()) {
            //6.不存在，返回错误
            return Result.fail("商铺类型不存在！");
        }
        for (ShopType type : shopTypes) {
            shopTypeListJson.add(JSONUtil.toJsonStr(type));
        }
        //7.将数据写入redis
        stringRedisTemplate.opsForList().rightPushAll(TYPE_SHOP_KEY, shopTypeListJson);
        stringRedisTemplate.expire(TYPE_SHOP_KEY, TYPE_SHOP_TTL, TimeUnit.MINUTES);
        //8.返回
        return Result.ok(shopTypes);
    }
}
