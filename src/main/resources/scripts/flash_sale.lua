--[[
  Flash Sale Atomic Purchase Script

  KEYS[1] = fs:fsp:{fspId}:stock          -- Sale-allocated stock counter
  KEYS[2] = fs:user:{userId}:daily:{date} -- User daily flash sale purchase flag
  KEYS[3] = fs:fsp:{fspId}:start          -- Sale start epoch millis
  KEYS[4] = fs:fsp:{fspId}:end            -- Sale end epoch millis

  ARGV[1] = current epoch millis

  Return codes:
    1  = Success — stock decremented, user flag set
   -1  = Sale has not started yet
   -2  = Sale has already ended
   -3  = User already purchased a flash sale product today
   -4  = Out of stock
]]

local stockKey   = KEYS[1]
local userKey    = KEYS[2]
local startKey   = KEYS[3]
local endKey     = KEYS[4]

local now = tonumber(ARGV[1])

-- 1. Check sale window
local startTime = tonumber(redis.call('GET', startKey))
if startTime == nil or now < startTime then
    return -1
end

local endTime = tonumber(redis.call('GET', endKey))
if endTime == nil or now > endTime then
    return -2
end

-- 2. Check user daily limit (1 flash sale purchase per user per day)
if redis.call('EXISTS', userKey) == 1 then
    return -3
end

-- 3. Check and decrement stock atomically
local stock = tonumber(redis.call('GET', stockKey))
if stock == nil or stock <= 0 then
    return -4
end

redis.call('DECR', stockKey)

-- 4. Set user daily purchase flag with TTL until end-of-day (max 86400s)
redis.call('SET', userKey, '1', 'EX', 86400)

return 1
