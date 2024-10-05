-- 锁的key KEYS[1] ,当前线程标示  ARGV[1]
-- 获取所中的线程标示 get key
-- 比较线程标示 与锁中标示 是否一致
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 释放锁 del key
    return redis.call('del', KEYS[1])
end
return 0