# hello-spring-ai-rag

## 개요

- Spring Boot + Spring AI + Ollama + Milvus 기반 RAG 예제 프로젝트
- Docker Compose로 로컬 LLM/벡터DB 환경을 구성하고 애플리케이션을 실행합니다

## 요구사항

- `Java 21`
- `Docker` 및 `Docker Compose`
- `Gradle`(래퍼 포함): `./gradlew`

## Docker 서비스 구성

- `ollama` 서버: `http://localhost:11434`
- `milvus-standalone`: 포트 `19530`(DB), `9091`(헬스체크)
- `etcd`, `minio`는 Milvus 의존 서비스로 함께 기동
- `open-webui`: `http://localhost:3000` (옵션)
- 데이터 볼륨: `./data/ollama`, `./data/milvus`, `./data/minio`, `./data/etcd`, `./data/open-webui`

## 시작하기

- 컨테이너 기동: `docker compose up -d`
- Ollama 모델 설치(컨테이너 내부 실행):
  - `docker exec -it ollama ollama pull nomic-embed-text`
  - `docker exec -it ollama ollama pull gemma:2b-instruct`
- 확인:
  - 모델 목록: `docker exec -it ollama ollama list`
  - Ollama 버전: `curl http://localhost:11434/api/version`
  - Milvus 헬스체크: `curl http://localhost:9091/healthz`

## 애플리케이션 실행

- 기본 실행: `./gradlew bootRun --args=--server.port=8080`
- 주요 설정(`src/main/resources/application.yaml` 기본값):
  - `spring.ai.ollama.base-url`: `http://localhost:11434`
  - `spring.ai.ollama.embedding.options.model`: `nomic-embed-text`
  - `spring.ai.ollama.chat.options.model`: `gemma:2b-instruct`
- 환경변수로 변경 가능:
  - `export OLLAMA_BASE_URL=http://localhost:11434`
  - `export OLLAMA_EMBED_MODEL=mxbai-embed-large` (예시)
  - `export OLLAMA_CHAT_MODEL=llama3.2:latest` (예시)
  - Milvus 관련: `MILVUS_DATABASE`, `MILVUS_COLLECTION`, `MILVUS_INIT_SCHEMA`, `MILVUS_EMBED_DIM`, `MILVUS_INDEX_TYPE`, `MILVUS_METRIC_TYPE`

## 문제 해결

- HTTP 404: `model "..." not found` → 해당 모델을 먼저 풀하세요(`ollama pull ...`).
- MacOS에서 DNS 네이티브 경고 → 프로젝트에 Mac용 네이티브 리졸버가 포함되어 있어 자동 처리됩니다.
- 슬림 컨테이너 디버깅이 필요하면: `docker debug ollama`로 디버그 셸 진입 가능.

## 종료 및 정리

- 컨테이너 중지: `docker compose down`
- 데이터 유지: 볼륨/`./data`는 유지됩니다. 초기화가 필요하면 수동 삭제에 주의하세요.

## 요청 흐름: POST /api/v1/chat

- 고수준 흐름(코드 설명 제외)

  - 클라이언트 → Spring Boot(API) → Milvus(VectorStore 유사도 검색) → Spring Boot(프롬프트 구성) → Ollama → Gemma:2b-instruct LLM → Spring Boot → 클라이언트

- 엔드포인트

  - 컨트롤러: `src/main/kotlin/com/weproud/api/v1/chat/ChatController.kt:21`(text), `src/main/kotlin/com/weproud/api/v1/chat/ChatController.kt:27`(stream)
  - 요청 DTO: `src/main/kotlin/com/weproud/api/v1/chat/dto/ChatDtos.kt:6`
  - `/api/v1/chat`: `Accept: text/plain`, 본문 JSON `{ query, topK?, label? }`
  - `/api/v1/chat/stream`: `Accept: text/event-stream`(SSE)

- 전체 처리 단계

  1. 입력 검증: `@Validated`/`@Valid`로 요청을 검증(`src/main/kotlin/com/weproud/api/v1/chat/ChatController.kt:14`, `src/main/kotlin/com/weproud/api/v1/chat/ChatController.kt:24`, `src/main/kotlin/com/weproud/api/v1/chat/ChatController.kt:31`)
  2. 서비스 호출: 컨트롤러가 `ChatService.stream(req)` 호출(`src/main/kotlin/com/weproud/api/v1/chat/ChatController.kt:25`, `src/main/kotlin/com/weproud/api/v1/chat/ChatController.kt:32`, `src/main/kotlin/com/weproud/api/v1/chat/ChatService.kt:43`)
  3. 유사도 검색 파라미터 구성: `SearchRequest`에 `query`, `topK`, 선택적 `label` 필터 적용(`src/main/kotlin/com/weproud/api/v1/chat/ChatService.kt:45-53`)
  4. 벡터 검색 수행: Milvus 기반 `VectorStore.similaritySearch(...)`로 후보 문서 조회(`src/main/kotlin/com/weproud/api/v1/chat/ChatService.kt:55`)
  5. 컨텍스트 선별: Q/A 형식 우선 고려, 질문 토큰 교집합 크기 기준으로 최적 문서 선택. 비거나 공백이면 `topK*2`로 재검색 후 동일 규칙 적용(`src/main/kotlin/com/weproud/api/v1/chat/ChatService.kt:56-66`, `src/main/kotlin/com/weproud/api/v1/chat/ChatService.kt:67-81`)
  6. 프롬프트 구성: `system.st`, `user.st` 템플릿으로 `Prompt` 생성(`src/main/kotlin/com/weproud/api/v1/chat/ChatService.kt:83-92`)
  7. LLM 호출/스트리밍: `ChatClient.prompt(prompt).stream().content()`로 모델 출력을 스트림으로 획득(`src/main/kotlin/com/weproud/api/v1/chat/ChatService.kt:96-99`)
  8. 응답 조립:
     - `/chat`: 스트림을 모아 하나의 문자열로 반환(`src/main/kotlin/com/weproud/api/v1/chat/ChatController.kt:24-25`)
     - `/chat/stream`: 스트림을 그대로 SSE로 전달(`src/main/kotlin/com/weproud/api/v1/chat/ChatController.kt:31-32`)

- 프롬프트 템플릿

  - 시스템: `src/main/resources/prompts/system.st`(컨텍스트의 Q와 가장 유사한 Q를 선택하고 해당 A만 출력)
  - 사용자: `src/main/resources/prompts/user.st`(`{query}`, `{context}` 바인딩)

- 핵심 구성 및 환경 변수

  - Ollama: `spring.ai.ollama.base-url`, `spring.ai.ollama.chat.options.model`, `spring.ai.ollama.embedding.options.model`(`src/main/resources/application.yaml`)
    - 환경 변수: `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL`, `OLLAMA_EMBED_MODEL`, `OLLAMA_TEMPERATURE`
  - Milvus(VectorStore): `spring.ai.vectorstore.milvus.*`(`src/main/resources/application.yaml`)
    - 환경 변수: `MILVUS_DATABASE`, `MILVUS_COLLECTION`, `MILVUS_INIT_SCHEMA`, `MILVUS_EMBED_DIM`, `MILVUS_INDEX_TYPE`, `MILVUS_METRIC_TYPE`
  - `ChatClient` 빈 구성: `src/main/kotlin/com/weproud/config/ChatConfig.kt:10-11`

- 외부 서비스 연동

  - Ollama(Chat/Embedding): `ChatClient`를 통해 채팅, VectorStore 적재 시 임베딩 자동 호출
  - Milvus(VectorStore): 유사도 검색과 문서 적재에 사용(`src/main/kotlin/com/weproud/api/v1/vector/VectorStoreService.kt:27-33`, `src/main/kotlin/com/weproud/api/v1/vector/VectorStoreService.kt:121-124`, `src/main/kotlin/com/weproud/api/v1/vector/VectorStoreService.kt:136-139`)

- 샘플 요청
  - `http/api.http:4-12`, `http/api.http:15-23` 참고
