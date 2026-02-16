import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PASSWORD = __ENV.PASSWORD || '1234aabb';
const RATE = parseInt(__ENV.RATE || '500', 10);
const DURATION = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    purchase: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: RATE,
      maxVUs: RATE + 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.5'],
    http_req_duration: ['p(95)<3000'],
  },
};

// 1. SETUP: RUN BEFORE TEST
export function setup() {
  console.log("⏳ [SETUP] Initialing Flash Sale Test Data...");

  // Step A: Get all flash sale product items
  const itemsRes = http.get(`${BASE_URL}/api/v1/flash-sale/items`);
  let items = JSON.parse(itemsRes.body).data;
  if (!items || items.length === 0) throw new Error("No items found or no flash safe active");
  
  // Push all flashSaleProductId to array
  const productIds = items.map(item => item.flashSaleProductId);
  console.log(`✅ Load flash sale product list ${productIds.length} FlashSaleProductIds: ${productIds.join(', ')}`);

  // Step B: Log in (Pre-warm 500 users)
  console.log("⏳ [SETUP] Login for 500 VUs...");
  let reqs = [];
  for (let i = 1; i <= 500; i++) { 
    reqs.push({
      method: 'POST',
      url: `${BASE_URL}/api/v1/auth/login`,
      body: JSON.stringify({ identifier: `test${i}@testmail.com`, password: PASSWORD }),
      params: { headers: { 'Content-Type': 'application/json' } }
    });
  }

  let responses = http.batch(reqs);
  let tokens = [];
  responses.forEach((res, index) => {
    if (res.status === 200) {
      tokens.push(res.json('data.token'));
    } else {
      console.warn(`[SETUP] Login failed for user test${index+1}: ${res.status}`);
    }
  });

  if (tokens.length === 0) throw new Error("Cannot get any jwt!");
  console.log(`🚀 [SETUP] Completed! Preparing ${tokens.length} Tokens & ${productIds.length} Items. Now load test ...!`);

  return {
    productIds: productIds,
    tokens: tokens
  };
}

// 2. RANDOM USER BUY RANDOM ITEM
export default function (data) {
  const randomToken = data.tokens[Math.floor(Math.random() * data.tokens.length)];
  
  const randomProductId = data.productIds[Math.floor(Math.random() * data.productIds.length)];

  const payload = JSON.stringify({ flashSaleProductId: randomProductId });
  
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${randomToken}`,
    },
  };

  const purchaseRes = http.post(`${BASE_URL}/api/v1/flash-sale/purchase`, payload, params);

  const purchaseOk = check(purchaseRes, {
    'purchase status 200': (r) => r.status === 200,
  });

  if (!purchaseOk || (__ITER % 100 === 0)) {
    const orderNo = purchaseOk && purchaseRes.json('data') ? purchaseRes.json('data.orderNo') : null;
    const msg = purchaseOk
      ? `fspId=${randomProductId} status=200 orderNo=${orderNo}`
      : `fspId=${randomProductId} status=${purchaseRes.status} body=${purchaseRes.body}`;
    console.log(`[VU ${__VU}] ${msg}`);
  }
}