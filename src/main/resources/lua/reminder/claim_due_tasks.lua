-- 原子领取到期任务
local dueIds = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
local result = {}
-- 1. 获取到期任务的ID列表
-- ZRANGEBYSCORE KEYS[1] -inf ARGV[1] LIMIT 0 ARGV[2]
-- 在 pending队列中，查找Score在0到现在之间的任务
-- 显示获取pollLimit条记录
for _, taskId in ipairs(dueIds) do
    -- 从待处理队列中删除这个任务
    if redis.call('ZREM', KEYS[1], taskId) == 1 then
        -- 将任务移动到处理中的队列中，score设置过期时间
        redis.call('ZADD', KEYS[3], ARGV[3], taskId)
        -- 从Hash中获取具体的任务数据
        local payload = redis.call('HGET', KEYS[2], taskId)
        if payload then
            table.insert(result, payload)
        else
            -- 如果Hash里面没有数据的话，那就把处理中的队列的任务删除掉
            redis.call('ZREM', KEYS[3], taskId)
        end
    end
end

return result
