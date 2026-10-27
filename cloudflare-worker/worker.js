/**
 * Pencode GitHub App Cloudflare Worker Backend
 *
 * App Name: Pencode
 * App ID: 5369463
 * Client ID: Iv23liJ1YMYgjNpHUToe
 *
 * Secrets to configure in Cloudflare Worker (wrangler secret put <NAME> or via Dashboard):
 * - GITHUB_CLIENT_SECRET: Your GitHub App Client Secret
 * - GITHUB_PRIVATE_KEY: Your GitHub App Private Key (.pem file content)
 * - GITHUB_CLIENT_ID: (Optional, defaults to Iv23liJ1YMYgjNpHUToe)
 * - GITHUB_APP_ID: (Optional, defaults to 5369463)
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const origin = request.headers.get("Origin") || "*";

    // Handle CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": origin,
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization, x-session-id",
          "Access-Control-Max-Age": "86400",
        },
      });
    }

    const corsHeaders = {
      "Access-Control-Allow-Origin": origin,
      "Access-Control-Allow-Headers": "Content-Type, Authorization, x-session-id",
    };

    try {
      // 1. Health check & status
      if (url.pathname === "/" || url.pathname === "/status") {
        return new Response(
          JSON.stringify({
            status: "ok",
            app: "Pencode GitHub Backend",
            app_id: env.GITHUB_APP_ID || "5369463",
            client_id: env.GITHUB_CLIENT_ID || "Iv23liJ1YMYgjNpHUToe",
            configured: {
              client_secret: Boolean(env.GITHUB_CLIENT_SECRET),
              private_key: Boolean(env.GITHUB_PRIVATE_KEY),
            },
            timestamp: new Date().toISOString(),
          }),
          {
            headers: { "Content-Type": "application/json", ...corsHeaders },
          }
        );
      }

      // 2. OAuth Token Exchange (Code + Client Secret -> Access Token)
      if (
        (url.pathname === "/api/oauth/token" || url.pathname === "/oauth/callback" || url.pathname === "/api/oauth/refresh") &&
        request.method === "POST"
      ) {
        const body = await request.json().catch(() => ({}));
        const code = body.code || url.searchParams.get("code");
        const refreshToken = body.refresh_token || url.searchParams.get("refresh_token");
        const clientId = body.client_id || env.GITHUB_CLIENT_ID || "Iv23liJ1YMYgjNpHUToe";
        const clientSecret = env.GITHUB_CLIENT_SECRET;

        if (!code && !refreshToken) {
          return new Response(
            JSON.stringify({ error: "Missing 'code' or 'refresh_token' parameter in request" }),
            { status: 400, headers: { "Content-Type": "application/json", ...corsHeaders } }
          );
        }

        if (!clientSecret) {
          return new Response(
            JSON.stringify({
              error: "Worker GITHUB_CLIENT_SECRET is not configured yet.",
              tip: "Set GITHUB_CLIENT_SECRET in Cloudflare Worker environment variables or secrets.",
            }),
            { status: 500, headers: { "Content-Type": "application/json", ...corsHeaders } }
          );
        }

        const payload = refreshToken
          ? {
              client_id: clientId,
              client_secret: clientSecret,
              grant_type: "refresh_token",
              refresh_token: refreshToken,
            }
          : {
              client_id: clientId,
              client_secret: clientSecret,
              code: code,
            };

        // Exchange code or refresh token with GitHub
        const tokenRes = await fetch("https://github.com/login/oauth/access_token", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify(payload),
        });

        const tokenData = await tokenRes.json();

        // If successful, fetch user details
        if (tokenData.access_token) {
          const userRes = await fetch("https://api.github.com/user", {
            headers: {
              Authorization: `Bearer ${tokenData.access_token}`,
              "User-Agent": "Pencode-Worker/1.0",
              Accept: "application/vnd.github.v3+json",
            },
          });
          if (userRes.ok) {
            tokenData.user = await userRes.json();
          }
        }

        return new Response(JSON.stringify(tokenData), {
          status: tokenRes.status,
          headers: { "Content-Type": "application/json", ...corsHeaders },
        });
      }

      // 3. GitHub App Installation Token Generation (RS256 JWT)
      if (url.pathname === "/api/app/token" && request.method === "POST") {
        const appId = env.GITHUB_APP_ID || "5369463";
        const privateKey = env.GITHUB_PRIVATE_KEY;
        const body = await request.json().catch(() => ({}));
        const installationId = body.installation_id;

        if (!privateKey) {
          return new Response(
            JSON.stringify({ error: "GITHUB_PRIVATE_KEY secret is not set in Worker." }),
            { status: 500, headers: { "Content-Type": "application/json", ...corsHeaders } }
          );
        }

        const jwt = await generateGitHubAppJwt(appId, privateKey);

        if (!installationId) {
          // List installations
          const installRes = await fetch("https://api.github.com/app/installations", {
            headers: {
              Authorization: `Bearer ${jwt}`,
              "User-Agent": "Pencode-Worker/1.0",
              Accept: "application/vnd.github.v3+json",
            },
          });
          const installations = await installRes.json();
          return new Response(JSON.stringify({ jwt, installations }), {
            headers: { "Content-Type": "application/json", ...corsHeaders },
          });
        }

        // Get installation access token
        const tokenRes = await fetch(
          `https://api.github.com/app/installations/${installationId}/access_tokens`,
          {
            method: "POST",
            headers: {
              Authorization: `Bearer ${jwt}`,
              "User-Agent": "Pencode-Worker/1.0",
              Accept: "application/vnd.github.v3+json",
            },
          }
        );

        const tokenData = await tokenRes.json();
        return new Response(JSON.stringify(tokenData), {
          status: tokenRes.status,
          headers: { "Content-Type": "application/json", ...corsHeaders },
        });
      }

      // 4. MCP JSON-RPC Server Endpoint
      if (url.pathname === "/mcp") {
        return handleMcpRequest(request, env, corsHeaders);
      }

      return new Response(
        JSON.stringify({ error: "Not Found", path: url.pathname }),
        { status: 404, headers: { "Content-Type": "application/json", ...corsHeaders } }
      );
    } catch (err) {
      return new Response(
        JSON.stringify({ error: err.message || "Internal Server Error" }),
        { status: 500, headers: { "Content-Type": "application/json", ...corsHeaders } }
      );
    }
  },
};

// Helper: Signs RS256 JWT for GitHub App
async function generateGitHubAppJwt(appId, privateKeyPem) {
  const cleanPem = privateKeyPem
    .replace(/-----BEGIN (?:RSA )?PRIVATE KEY-----/, "")
    .replace(/-----END (?:RSA )?PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");

  const binaryDer = Uint8Array.from(atob(cleanPem), (c) => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryDer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iat: now - 60,
    exp: now + 600,
    iss: appId,
  };

  const header = { alg: "RS256", typ: "JWT" };
  const base64UrlEncode = (obj) =>
    btoa(JSON.stringify(obj))
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");

  const unsignedToken = `${base64UrlEncode(header)}.${base64UrlEncode(payload)}`;
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    new TextEncoder().encode(unsignedToken)
  );

  const base64Signature = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");

  return `${unsignedToken}.${base64Signature}`;
}

// Helper: Handles MCP JSON-RPC requests
async function handleMcpRequest(request, env, corsHeaders) {
  if (request.method !== "POST") {
    return new Response(JSON.stringify({ jsonrpc: "2.0", result: { name: "Pencode GitHub MCP" } }), {
      headers: { "Content-Type": "application/json", ...corsHeaders },
    });
  }

  const rpc = await request.json().catch(() => ({}));
  const authHeader = request.headers.get("Authorization") || "";

  if (rpc.method === "tools/list") {
    return new Response(
      JSON.stringify({
        jsonrpc: "2.0",
        id: rpc.id,
        result: {
          tools: [
            {
              name: "github_list_repositories",
              description: "List repositories accessible to the user.",
              inputSchema: { type: "object", properties: {} },
            },
            {
              name: "github_trigger_workflow",
              description: "Trigger a workflow dispatch on GitHub Actions.",
              inputSchema: {
                type: "object",
                properties: {
                  repo: { type: "string" },
                  workflow: { type: "string" },
                  branch: { type: "string" },
                },
                required: ["repo", "workflow"],
              },
            },
          ],
        },
      }),
      { headers: { "Content-Type": "application/json", ...corsHeaders } }
    );
  }

  return new Response(
    JSON.stringify({ jsonrpc: "2.0", id: rpc.id, result: { status: "processed" } }),
    { headers: { "Content-Type": "application/json", ...corsHeaders } }
  );
}
