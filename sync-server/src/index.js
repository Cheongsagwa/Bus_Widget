/**
 * 춘천버스 위젯 — 즐겨찾기 동기화 서버 (Cloudflare Workers)
 *
 * 앱이 네이버 로그인으로 받은 access token 을 Authorization 헤더에 실어 보내면,
 * 네이버에 그 토큰이 누구 것인지 물어보고(회원 id), 그 id 칸에 즐겨찾기를 저장/조회한다.
 *
 *   GET    /favorites   → 저장된 즐겨찾기 ({stops, routes, order, updatedAt})
 *   PUT    /favorites   → 즐겨찾기 저장 (본문: {stops, routes, order})
 *   DELETE /favorites   → 저장된 즐겨찾기 삭제 (계정 연결 해제 시)
 *
 * 네이버 회원 id 는 앱마다 다르게 발급되므로, 다른 앱에서 받은 토큰으로는 이 앱 사용자의 칸을 읽을 수 없다.
 */

const EMPTY = { stops: [], routes: [], order: [], updatedAt: 0 };
const MAX_BODY = 100_000; // 즐겨찾기 JSON 최대 크기 (넉넉하게 100KB)
const MAX_ITEMS = 500;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== "/favorites") return json({ error: "not_found" }, 404);

    const auth = request.headers.get("Authorization") || "";
    if (!auth.startsWith("Bearer ")) return json({ error: "unauthorized" }, 401);

    // 1) 네이버에 토큰 주인을 확인한다
    const me = await fetch("https://openapi.naver.com/v1/nid/me", {
      headers: { Authorization: auth },
    });
    if (me.status === 401) return json({ error: "invalid_token" }, 401);
    if (!me.ok) return json({ error: "naver_unavailable" }, 502);
    const profile = await me.json().catch(() => null);
    const userId = profile && profile.response && profile.response.id;
    if (!userId) return json({ error: "invalid_token" }, 401);

    const key = "fav:" + userId;

    // 2) 요청 처리
    switch (request.method) {
      case "GET": {
        const saved = await env.FAVORITES.get(key);
        return new Response(saved || JSON.stringify(EMPTY), { headers: JSON_HEADERS });
      }
      case "PUT": {
        const text = await request.text();
        if (text.length > MAX_BODY) return json({ error: "too_large" }, 413);
        let data;
        try {
          data = JSON.parse(text);
        } catch (_) {
          return json({ error: "bad_json" }, 400);
        }
        const clean = {
          stops: strings(data.stops),
          routes: strings(data.routes),
          order: strings(data.order),
          updatedAt: Date.now(),
        };
        await env.FAVORITES.put(key, JSON.stringify(clean));
        return json({ ok: true, updatedAt: clean.updatedAt });
      }
      case "DELETE": {
        await env.FAVORITES.delete(key);
        return json({ ok: true });
      }
      default:
        return json({ error: "method_not_allowed" }, 405);
    }
  },
};

const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8" };

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

/** 문자열 배열만 받아들인다 (항목 수·길이 제한) */
function strings(value) {
  if (!Array.isArray(value)) return [];
  return value
    .filter((v) => typeof v === "string" && v.length > 0 && v.length < 500)
    .slice(0, MAX_ITEMS);
}
