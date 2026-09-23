# 즐겨찾기 동기화 서버 (Cloudflare Workers)

네이버로 로그인한 사용자의 즐겨찾기를 저장해 두는 작은 서버입니다.
앱을 지웠다 다시 깔아도 네이버로 로그인하면 즐겨찾기가 돌아옵니다.

무료 요금제로 충분합니다 (하루 10만 요청, 카드 등록 없음).

## 처음 한 번만 하는 설정

PC 에 Node.js 가 있어야 합니다 (https://nodejs.org — LTS 설치).
이 폴더(`sync-server`)에서 터미널을 열고 차례로 실행하세요.

```bash
# 1) Cloudflare 로그인 (브라우저가 열리면 가입/로그인 후 허용)
npx wrangler login

# 2) 즐겨찾기 저장소(KV) 만들기
npx wrangler kv namespace create FAVORITES
```

2번 결과에 나오는 `id = "..."` 값을 `wrangler.toml` 의 `여기에_KV_id_붙여넣기` 자리에 붙여넣습니다.

```bash
# 3) 서버 올리기
npx wrangler deploy
```

마지막에 `https://ccbus-sync.<내 이름>.workers.dev` 같은 주소가 나옵니다.
이 주소를 프로젝트 최상위의 `local.properties` 에 넣고 앱을 다시 빌드하세요.

```
sync.url=https://ccbus-sync.<내 이름>.workers.dev
```

## 서버 코드를 고친 뒤

`npx wrangler deploy` 만 다시 실행하면 됩니다.
