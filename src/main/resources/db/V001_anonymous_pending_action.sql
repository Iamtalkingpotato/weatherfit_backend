-- 비로그인 사용자의 행동 이벤트를 임시 저장하는 테이블
-- 로그인 시 실제 고객 데이터로 이관 후 삭제됨
CREATE TABLE IF NOT EXISTS anonymous_pending_action (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    anonymous_id VARCHAR(100) NOT NULL COMMENT '비로그인 사용자 임시 ID (uid_xxx)',
    event_type   VARCHAR(50)  COMMENT '이벤트 타입 (wishlist_add, add_to_cart 등)',
    data_json    TEXT         COMMENT '원본 Kafka 메시지 JSON',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_anon_pending_id (anonymous_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
