-- 处理中超时回补
-- 1.查找超时任务
-- 从正在执行任务的队列中查找score值为负无穷到现在的任务
local expiredIds = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
local recovered = 0

for _, taskId in ipairs(expiredIds) do
    -- 从Hash中查找任务数据是否还存在
    if redis.call('HEXISTS', KEYS[3], taskId) == 1 then
        -- 如果数据还存在，那么将这个任务重新放到待处理的队列中
        redis.call('ZADD', KEYS[2], ARGV[3], taskId)
    end
    -- 将这个任务从处理中移除掉
    redis.call('ZREM', KEYS[1], taskId)
    recovered = recovered + 1
end

return recovered
