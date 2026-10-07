// SpeedyWatch AI worker - Cloudflare Workers AI behind a Bearer token.
// Mirrors the GMB-Extractor share worker's AI surface:
//   GET  /api/ai/models - text-generation catalog for the Settings model picker
//   POST /api/ai/chat   - {model, messages, max_tokens} -> OpenAI-shaped choices
// All /api/* routes require `Authorization: Bearer AUTH_TOKEN`.

const AI_CHAT_MODEL_CATALOG = [
  { id: '@cf/meta/llama-3.1-8b-instruct-fp8-fast', default: true, desc: 'Default. Fastest verified, reliable strict-JSON output.' },
  { id: '@cf/meta/llama-3.3-70b-instruct-fp8-fast', desc: 'Strongest Llama. Strict JSON verified.' },
  { id: '@cf/openai/gpt-oss-20b', desc: 'OpenAI open-weights. Strict JSON verified.' },
  { id: '@cf/openai/gpt-oss-120b', desc: 'Best quality open-weights.' },
  { id: '@cf/mistralai/mistral-small-3.1-24b-instruct', desc: 'Strict JSON verified.' }
];
const AI_CHAT_DEFAULT_MODEL = '@cf/meta/llama-3.1-8b-instruct-fp8-fast';

function jsonResponse(body, status, extraHeaders) {
  return new Response(JSON.stringify(body), {
    status,
    headers: Object.assign(
      { 'Content-Type': 'application/json; charset=utf-8' },
      extraHeaders || {}
    )
  });
}

async function handleAiChat(request, env, corsHeaders) {
  if (!env.AI) {
    return jsonResponse(
      { error: 'AI binding not configured. Add [ai] to wrangler.toml.' },
      500,
      corsHeaders
    );
  }
  let body;
  try {
    body = await request.json();
  } catch (_) {
    return jsonResponse({ error: 'Invalid JSON body' }, 400, corsHeaders);
  }
  const messages = Array.isArray(body && body.messages) ? body.messages : null;
  if (!messages || messages.length === 0) {
    return jsonResponse({ error: 'messages array required' }, 400, corsHeaders);
  }
  const allowed = new Set(AI_CHAT_MODEL_CATALOG.map((m) => m.id));
  const model = allowed.has(body.model) ? body.model : AI_CHAT_DEFAULT_MODEL;
  const maxTokens = Number.isFinite(Number(body.max_tokens))
    ? Math.min(Math.max(1, Math.floor(Number(body.max_tokens))), 4096)
    : 2048;
  try {
    const result = await env.AI.run(model, {
      messages,
      max_tokens: maxTokens
    });
    const content = result && typeof result.response === 'string'
      ? result.response
      : (result && result.choices && result.choices[0] && result.choices[0].message
          ? String(result.choices[0].message.content || '')
          : '');
    return jsonResponse({
      model,
      choices: [{ index: 0, message: { role: 'assistant', content } }]
    }, 200, corsHeaders);
  } catch (error) {
    return jsonResponse(
      { error: 'AI request failed: ' + (error && error.message ? error.message : 'unknown') },
      502,
      corsHeaders
    );
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    };
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    if (path === '/api/health' && request.method === 'GET') {
      return jsonResponse({ ok: true, ai: !!env.AI }, 200, corsHeaders);
    }

    // All other /api/* routes require the shared Bearer token.
    const authHeader = request.headers.get('Authorization') || '';
    if (!authHeader.startsWith('Bearer ') || authHeader.slice(7) !== env.AUTH_TOKEN) {
      return jsonResponse({ error: 'Unauthorized: Invalid auth token' }, 401, corsHeaders);
    }

    if (path === '/api/ai/chat' && request.method === 'POST') {
      return handleAiChat(request, env, corsHeaders);
    }
    if (path === '/api/ai/models' && request.method === 'GET') {
      return jsonResponse({ models: AI_CHAT_MODEL_CATALOG }, 200, corsHeaders);
    }

    return jsonResponse({ error: 'Not found' }, 404, corsHeaders);
  }
};
