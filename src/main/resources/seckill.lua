---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by dyy.
--- DateTime: 2024/4/6 19:47
---
-- 1.参数列表
-- 1.1 优惠券id
local voucherId = ARGV[1]
-- 1.2用户id
local userId = ARGV[2]
-- 1.3 订单id =
local orderId = ARGV[3]

-- 2数据key
-- 2.1 库存key  .. 相当于java中+，字符拼接
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2 订单key
local orderKey = 'seckill:order:' .. voucherId

-- 3脚本业务
-- 3.1 判断库存是否充足，由于使用string类型存入redis所以取出库存是字符串，因此需要先转为数值类型
if(tonumber(redis.call('get',stockKey))<=0) then
    -- 3.1.1库存不足返回1
    return 1
end
-- 3.2 判断用户是否下单
if(redis.call('sismember',orderKey,userId)==1) then
    -- 3.2.1用户已下单返回2
    return 2
end
-- 3.4 扣库存
redis.call('incrby',stockKey,'-1')
-- 3.5 下单（保存用户id到订单key中）
redis.call('sadd',orderKey,userId)

-- 3.6 发送消息到队列中  x add stream.orders * k1 v1 k2 v2
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)

-- 成功返回0
return 0