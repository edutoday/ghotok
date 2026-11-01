import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const PRODUCT_TIERS: Record<string, string> = {
  ghotok_plan_silver: "Silver",
  ghotok_plan_gold: "Gold",
  ghotok_plan_diamond: "Diamond",
};

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function base64UrlEncodeText(value: string): string {
  return base64UrlEncode(new TextEncoder().encode(value));
}

function base64UrlDecode(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(normalized + "=".repeat((4 - normalized.length % 4) % 4));
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

async function getGoogleAccessToken(): Promise<string> {
  const serviceAccountEmail = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL")?.trim() ?? "";
  const privateKeyPem = Deno.env.get("GOOGLE_PLAY_PRIVATE_KEY")?.replace(/\\n/g, "\n").trim() ?? "";
  if (!serviceAccountEmail || !privateKeyPem) {
    throw new Error("Google Play verification credentials are not configured.");
  }

  const privateKeyBase64 = privateKeyPem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    base64UrlDecode(privateKeyBase64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "")),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const nowSeconds = Math.floor(Date.now() / 1000);
  const header = base64UrlEncodeText(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64UrlEncodeText(JSON.stringify({
    iss: serviceAccountEmail,
    scope: PUBLISHER_SCOPE,
    aud: TOKEN_URL,
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  }));
  const unsignedAssertion = `${header}.${claims}`;
  const signature = new Uint8Array(
    await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      privateKey,
      new TextEncoder().encode(unsignedAssertion),
    ),
  );
  const assertion = `${unsignedAssertion}.${base64UrlEncode(signature)}`;

  const tokenResponse = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!tokenResponse.ok) {
    throw new Error("Google Play service-account authorization failed.");
  }
  const tokenBody = await tokenResponse.json();
  if (typeof tokenBody.access_token !== "string") {
    throw new Error("Google Play returned an invalid authorization response.");
  }
  return tokenBody.access_token;
}

async function sha256Hex(value: string): Promise<string> {
  const digest = new Uint8Array(
    await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)),
  );
  return Array.from(digest, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

serve(async (request: Request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed." }, 405);
  }
  if (Number(request.headers.get("content-length") ?? "0") > 16_384) {
    return jsonResponse({ error: "Request is too large." }, 413);
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim().replace(/\/+$/, "") ?? "";
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")?.trim() ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim() ?? "";
    const packageName = Deno.env.get("GOOGLE_PLAY_PACKAGE_NAME")?.trim() ?? "";
    const authorization = request.headers.get("authorization") ?? "";
    const jwt = authorization.match(/^Bearer\s+(.+)$/i)?.[1] ?? "";
    if (!jwt || !supabaseUrl || !anonKey || !serviceRoleKey || !packageName) {
      return jsonResponse({ error: "Authentication or server configuration is unavailable." }, 401);
    }

    const authResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
      headers: { apikey: anonKey, Authorization: `Bearer ${jwt}` },
    });
    if (!authResponse.ok) {
      return jsonResponse({ error: "A valid signed-in account is required." }, 401);
    }
    const authUser = await authResponse.json();
    const userId = typeof authUser.id === "string" ? authUser.id : "";
    if (!userId) return jsonResponse({ error: "Authenticated user ID is unavailable." }, 401);

    const requestBody = await request.text();
    if (new TextEncoder().encode(requestBody).length > 16_384) {
      return jsonResponse({ error: "Request is too large." }, 413);
    }
    const body = JSON.parse(requestBody);
    const purchaseToken = typeof body.purchaseToken === "string" ? body.purchaseToken.trim() : "";
    const productId = typeof body.productId === "string" ? body.productId.trim() : "";
    if (!purchaseToken || purchaseToken.length > 8_192 || !PRODUCT_TIERS[productId]) {
      return jsonResponse({ error: "Invalid Google Play purchase details." }, 400);
    }

    const googleAccessToken = await getGoogleAccessToken();
    const encodedPackage = encodeURIComponent(packageName);
    const encodedProduct = encodeURIComponent(productId);
    const encodedToken = encodeURIComponent(purchaseToken);
    const purchaseUrl =
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodedPackage}` +
      `/purchases/subscriptionsv2/tokens/${encodedToken}`;
    const purchaseResponse = await fetch(purchaseUrl, {
      headers: { Authorization: `Bearer ${googleAccessToken}` },
    });
    if (!purchaseResponse.ok) {
      return jsonResponse({ error: "Google Play could not verify this purchase." }, 400);
    }

    const purchase = await purchaseResponse.json();
    const allowedStates = new Set([
      "SUBSCRIPTION_STATE_ACTIVE",
      "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
      "SUBSCRIPTION_STATE_CANCELED",
    ]);
    const lineItems = Array.isArray(purchase.lineItems) ? purchase.lineItems : [];
    const matchingLine = lineItems.find((line: Record<string, unknown>) =>
      line.productId === productId
    );
    const expiryTime = typeof matchingLine?.expiryTime === "string"
      ? Date.parse(matchingLine.expiryTime)
      : Number.NaN;
    const startTime = typeof purchase.startTime === "string"
      ? Date.parse(purchase.startTime)
      : Number.NaN;
    if (!allowedStates.has(purchase.subscriptionState) ||
        !matchingLine ||
        !Number.isFinite(expiryTime) ||
        expiryTime <= Date.now() ||
        !Number.isFinite(startTime) ||
        startTime <= 0) {
      return jsonResponse({ error: "The Google Play subscription is not currently entitled." }, 402);
    }

    if (purchase.acknowledgementState !== "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED") {
      const acknowledgeUrl =
        `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodedPackage}` +
        `/purchases/subscriptions/${encodedProduct}/tokens/${encodedToken}:acknowledge`;
      const acknowledgeResponse = await fetch(acknowledgeUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${googleAccessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({}),
      });
      if (!acknowledgeResponse.ok) {
        return jsonResponse({ error: "The verified purchase could not be acknowledged yet. Retry restore." }, 502);
      }
    }

    const tokenHash = await sha256Hex(purchaseToken);
    const latestOrderId = typeof purchase.latestOrderId === "string"
      ? purchase.latestOrderId
      : "";
    const grantResponse = await fetch(
      `${supabaseUrl}/rest/v1/rpc/apply_verified_google_play_purchase`,
      {
        method: "POST",
        headers: {
          apikey: serviceRoleKey,
          Authorization: `Bearer ${serviceRoleKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          p_user_id: userId,
          p_product_id: productId,
          p_purchase_token_hash: tokenHash,
          p_order_id: latestOrderId,
          p_activated_at_ms: startTime,
          p_expires_at_ms: expiryTime,
        }),
      },
    );
    if (!grantResponse.ok) {
      return jsonResponse({ error: "The verified purchase could not be applied to this account." }, 500);
    }

    const result = await grantResponse.json();
    if (!result || result.verified !== true || result.productId !== productId) {
      return jsonResponse({ error: "The purchase entitlement response was invalid." }, 500);
    }
    return jsonResponse(result, 200);
  } catch (error) {
    console.error(
      "Google Play purchase verification failed:",
      error instanceof Error ? error.message : "Unexpected verification error",
    );
    return jsonResponse({ error: "Google Play purchase verification failed. Please retry." }, 500);
  }
});
