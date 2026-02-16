--[[
  Flash Sale Atomic Purchase Script (with Balance)

  KEYS[1] = fs:fsp:{fspId}:stock          -- Allocated stock counter (integer)
  KEYS[2] = fs:user:{userId}:daily:{date} -- User daily purchase flag
  KEYS[3] = fs:fsp:{fspId}:price          -- Sale price in cents (integer)
  KEYS[4] = fs:user:{userId}:balance      -- User balance in cents (integer)

  ARGV[1] = ttl_seconds (integer) -- Seconds until midnight (current day).
           Key expires at end of day so user can purchase again next calendar day.

  Return codes:
    1  = Success — stock decremented, balance deducted, user flag set
   -1  = Item price not found in Redis
   -2  = User balance not found in Redis
   -3  = Insufficient balance
   -4  = User already purchased a flash sale product today
   -5  = Out of stock
]]

local stockKey   = KEYS[1]
local dailyKey   = KEYS[2]
local priceKey   = KEYS[3]
local balanceKey = KEYS[4]

-- 1. Get item price (cents)
local price = tonumber(redis.call('GET', priceKey))
if price == nil then
    return -1
end

-- 2. Get user balance (cents)
local balance = tonumber(redis.call('GET', balanceKey))
if balance == nil then
    return -2
end

-- 3. Check sufficient balance
if balance < price then
    return -3
end

-- 4. Check user daily limit (1 flash sale purchase per user per day)
if redis.call('EXISTS', dailyKey) == 1 then
    return -4
end

-- 5. Check stock availability
local stock = tonumber(redis.call('GET', stockKey))
if stock == nil or stock <= 0 then
    return -5
end

-- 6. TTL: seconds until midnight (so key expires at end of current day)
local ttl = tonumber(ARGV[1])
if ttl == nil or ttl < 1 then
    ttl = 1
end

-- 7. All checks passed — perform atomic mutations
redis.call('DECRBY', balanceKey, price)
redis.call('DECR', stockKey)
redis.call('SET', dailyKey, '1', 'EX', ttl)

return 1
