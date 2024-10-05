-- 1.参数列表
-- 1.1. 优惠券id
local voucherId = ARGV[1]
-- 1.2. 用户id
local userId = ARGV[2]
-- 1.3. 订单Id
local orderId = ARGV[3]

-- 2. 数据key
local seckillKey = "seckill:" .. voucherId
-- 2.1. 库存key
local stockKey = "seckill:stock:" .. voucherId
-- 2.2. 订单key
local orderKey = "seckill:order:" .. voucherId

local beginTime = "seckill:beginTime:" .. voucherId
local endTime = "seckill:endTime:" .. voucherId

local now = tonumber(redis.call('TIME')[1]) -- 获取当前时间戳
local begin = tonumber(redis.call('hget' ,seckillKey ,beginTime))
local ending = tonumber(redis.call('hget' ,seckillKey ,endTime))

if begin == nil then
    return 5 -- 无此活动
end

if ending == nil then
    return 5 -- 无此活动
end

if now < begin then
    return 3 -- 活动尚未开始
end

if now > ending then
    return 4 -- 活动已结束
end

-- 3. 脚本业务
-- 3.1. 判断库存是否充足 get stockKey
if(tonumber(redis.call('hget' ,seckillKey ,stockKey)) <= 0) then
    return 1
end
-- 3.2. 判断用户是否下过单 sismember orderKey userId
if(redis.call('sismember' ,orderKey ,userId) == 1 )then
    -- 存在，说明重复下单
   return 2
end
-- 3.3. 扣库存
redis.call('hincrby' ,seckillKey ,stockKey ,-1)
-- 3.4. 下单(保存用户)
redis.call('sadd' ,orderKey ,userId)

-- 3.5. 发送消息， XADD stream.orders * k1 v1 k2 v2
redis.call('xadd' ,'stream.orders' ,'*' ,'userId' ,userId ,'voucherId' ,voucherId ,'id' ,orderId)
return 0