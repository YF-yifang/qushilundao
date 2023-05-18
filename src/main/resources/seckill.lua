--1.参数列表
--1.1.优惠券id
local voucherId = ARGV[1]
--1.2.用户id
local userId = ARGV[2]
--1.3.订单id
local orderId = ARGV[3]
--1.4.当前系统时间戳
local nowTime = ARGV[4]

--2.数据key
--2.1.库存key
local stockKey = 'seckill:stock:' .. voucherId
--2.2.订单key
local orderKey = 'seckill:order:' .. voucherId

--3.脚本业务
--3.1.判断秒杀是否开始 HGET stockKey beginTime
if tonumber(redis.call('hget', stockKey, 'beginTime')) > tonumber(nowTime) then
    --秒杀未开始，返回1
    return 1
end
--3.2.判断秒杀是否结束 HGET stockKey endTime
if tonumber(redis.call('hget', stockKey, 'endTime')) < tonumber(nowTime) then
    --秒杀已经结束，返回2
    return 2
end
--3.3.判断库存是否充足 get stockKey
if (tonumber(redis.call('hget', stockKey, 'stock')) <= 0) then
    --库存不足，返回3
    return 3
end
--3.4.判断用户是否下单 SISMEMBER orderKey userId
if redis.call('sismember', orderKey, userId) == 1 then
    --存在，说明重复下单，返回4
    return 4
end
--3.5.扣库存 hincrby stockKey -1
redis.call('hincrby', stockKey, "stock", -1)
--3.6.下单（保存用户） sadd orderKey userId
redis.call('sadd', orderKey, userId)
--3.7.发送消息到队列中 XADD stream.orders * k1 v1 k2 v2
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0
