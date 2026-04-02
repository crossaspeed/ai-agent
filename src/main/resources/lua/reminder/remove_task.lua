-- 任务移除确认
local taskId = ARGV[1]

redis.call('ZREM', KEYS[1], taskId)
redis.call('ZREM', KEYS[3], taskId)
redis.call('HDEL', KEYS[2], taskId)

return 1
