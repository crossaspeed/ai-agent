-- 任务入队或重排
local taskId = ARGV[1]
local score = ARGV[2]
local payload = ARGV[3]

-- HASH 结构，存储具体要提醒得到消息 （payloadKey）taskId->JSON（提醒的内容）
redis.call('HSET', KEYS[2], taskId, payload)
-- ZSET 结构，时间已到，已经被“拉”出来交给 Java 代码去发提醒的任务 （processingKey）taskId->任务开始执行的时间
redis.call('ZREM', KEYS[3], taskId)
-- ZSET 结构，还没有到时间，准备执行的任务（indexKey）taskId->提醒时间
redis.call('ZADD', KEYS[1], score, taskId)

return 1
